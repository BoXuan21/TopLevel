package com.toplevel.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Reads a plan from an Excel (.xlsx) or CSV file into plain text the coach can read. */
object PlanImport {
    const val MAX_CHARS = 12_000

    fun read(ctx: Context, uri: Uri): String {
        val name = displayName(ctx, uri).lowercase()
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Couldn't open the file")
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val text = when {
            isZip -> xlsx(bytes)
            name.endsWith(".xls") -> throw IllegalArgumentException("Old .xls format: in Excel use Save as → .xlsx or .csv")
            else -> csv(String(bytes, Charsets.UTF_8).removePrefix("﻿"))
        }.trim()
        if (text.isBlank()) throw IllegalArgumentException("The file looks empty")
        return text.take(MAX_CHARS)
    }

    private fun displayName(ctx: Context, uri: Uri): String = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: ""
    } catch (_: Exception) { "" }

    /** CSV/TSV/semicolon → " | " separated rows. */
    fun csv(s: String): String {
        val first = s.lineSequence().firstOrNull().orEmpty()
        val sep = listOf('\t', ';', ',').maxByOrNull { c -> first.count { it == c } } ?: ','
        return s.lineSequence().map { line ->
            splitCsv(line, sep).map { it.trim() }.dropLastWhile { it.isEmpty() }.joinToString(" | ")
        }.filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun splitCsv(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>(); val cur = StringBuilder(); var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> q = !q
                c == sep && !q -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    /** Minimal .xlsx reader: shared strings + every worksheet, in order. */
    fun xlsx(bytes: ByteArray): String {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name.startsWith("xl/") && e.name.endsWith(".xml")) files[e.name] = z.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) }.orEmpty()
        val names = files["xl/workbook.xml"]?.let { sheetNames(it) }.orEmpty()
        val sheets = files.keys.filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        return sheets.mapIndexed { i, path ->
            val rows = sheet(files.getValue(path), shared)
            if (rows.isEmpty()) "" else buildString {
                append("## Sheet: ${names.getOrNull(i) ?: "Sheet ${i + 1}"}\n")
                rows.forEach { append(it).append('\n') }
            }
        }.filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun parser(b: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(b), "UTF-8") }

    private fun sharedStrings(b: ByteArray): List<String> {
        val p = parser(b); val out = mutableListOf<String>(); var cur: StringBuilder? = null; var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> cur = StringBuilder(); "t" -> inT = true }
                XmlPullParser.TEXT -> if (inT) cur?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "si" -> { out += cur.toString(); cur = null }; "t" -> inT = false }
            }
        }
        return out
    }

    private fun sheetNames(b: ByteArray): List<String> {
        val p = parser(b); val out = mutableListOf<String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") out += p.getAttributeValue(null, "name") ?: ""
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) { if (!c.isLetter()) break; n = n * 26 + (c.uppercaseChar() - 'A' + 1) }
        return (n - 1).coerceAtLeast(0)
    }

    private fun sheet(b: ByteArray, shared: List<String>): List<String> {
        val p = parser(b)
        val rows = mutableListOf<String>()
        var cells = sortedMapOf<Int, String>()
        var col = 0; var type = ""; var value = StringBuilder(); var inV = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> cells = sortedMapOf()
                    "c" -> { col = colIndex(p.getAttributeValue(null, "r") ?: ""); type = p.getAttributeValue(null, "t") ?: ""; value = StringBuilder() }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false
                    "c" -> {
                        val raw = value.toString()
                        val v = when (type) {
                            "s" -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty()
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw.toDoubleOrNull()?.let { d -> if (d % 1.0 == 0.0 && kotlin.math.abs(d) < 1e12) d.toLong().toString() else "%.2f".format(d) } ?: raw
                        }.trim()
                        if (v.isNotEmpty()) cells[col] = v
                    }
                    "row" -> if (cells.isNotEmpty()) {
                        val max = cells.lastKey()
                        rows += (0..max).joinToString(" | ") { cells[it].orEmpty() }.trimEnd(' ', '|')
                    }
                }
            }
        }
        return rows
    }
}
