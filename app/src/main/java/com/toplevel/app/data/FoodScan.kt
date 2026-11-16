package com.toplevel.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** A packaged product from Open Food Facts, values per 100 g. */
data class Product(
    val code: String,
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val servingG: Double?,
) {
    fun macrosFor(grams: Double) = Repo.Macros(
        name = "$name (${grams.roundToInt()} g)",
        kcal = (kcal100 * grams / 100).roundToInt(),
        protein = (protein100 * grams / 100).roundToInt(),
        carbs = (carbs100 * grams / 100).roundToInt(),
        fat = (fat100 * grams / 100).roundToInt(),
    )
}

object FoodScan {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Loads a photo, scaled so the long side is at most [maxSide] px. */
    fun loadBitmap(ctx: Context, uri: Uri, maxSide: Int = 1280): Bitmap {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            val s = info.size
            val scale = maxSide.toFloat() / maxOf(s.width, s.height)
            if (scale < 1f) dec.setTargetSize((s.width * scale).toInt(), (s.height * scale).toInt())
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    internal fun jpegBase64(b: Bitmap, quality: Int = 80): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Finds an EAN/UPC barcode in the photo, on-device. */
    suspend fun readBarcode(b: Bitmap): String? = suspendCancellableCoroutine { cont ->
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            ).build()
        )
        scanner.process(InputImage.fromBitmap(b, 0))
            .addOnSuccessListener { codes -> cont.resume(codes.firstNotNullOfOrNull { it.rawValue }) }
            .addOnFailureListener { cont.resumeWithException(it) }
            .addOnCompleteListener { scanner.close() }
    }

    /** Looks up a barcode in Open Food Facts. Null when unknown. */
    suspend fun lookup(code: String): Product? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,brands,nutriments,serving_quantity")
            .header("User-Agent", "TopLevel/1.0 (Android; personal tracker)")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            val o = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            if ((o["status"] as? JsonPrimitive)?.contentOrNull != "1") return@withContext null
            val p = o["product"]?.jsonObject ?: return@withContext null
            productOf(p, code)
        }
    }

    /** Text search ("nöm pro 35"). Results are cached per query. */
    private val searchCache = mutableMapOf<String, List<Product>>()
    suspend fun search(query: String): List<Product> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        searchCache[q]?.let { return@withContext it }
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("world.openfoodfacts.org").addPathSegments("cgi/search.pl")
            .addQueryParameter("search_terms", q).addQueryParameter("search_simple", "1")
            .addQueryParameter("action", "process").addQueryParameter("json", "1").addQueryParameter("page_size", "12")
            .addQueryParameter("fields", "code,product_name,brands,nutriments,serving_quantity").build()
        val req = Request.Builder().url(url).header("User-Agent", "TopLevel/1.0 (Android; personal tracker)").build()
        val out = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@use emptyList()
            val o = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            (o["products"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.let { p -> productOf(p, (p["code"] as? JsonPrimitive)?.contentOrNull ?: "") } }
                .distinctBy { it.name.lowercase() }
                .take(8)
        }
        searchCache[q] = out
        out
    }

    private fun num(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    /** Open Food Facts product JSON → [Product]; null without calorie data. */
    fun productOf(p: JsonObject, code: String): Product? {
        val n = p["nutriments"] as? JsonObject ?: return null
        val kcal = num(n, "energy-kcal_100g") ?: num(n, "energy-kj_100g")?.div(4.184) ?: num(n, "energy_100g")?.div(4.184) ?: return null
        val brand = when (val b = p["brands"]) {
            is JsonPrimitive -> b.contentOrNull
            is kotlinx.serialization.json.JsonArray -> (b.firstOrNull() as? JsonPrimitive)?.contentOrNull
            else -> null
        }?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        val name = listOfNotNull(
            (p["product_name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
            brand?.replaceFirstChar { it.uppercase() },
        ).joinToString(" · ").ifBlank { return null }
        return Product(code, name, kcal, num(n, "proteins_100g") ?: 0.0, num(n, "carbohydrates_100g") ?: 0.0,
            num(n, "fat_100g") ?: 0.0, num(p, "serving_quantity")?.takeIf { it > 0 })
    }
}
