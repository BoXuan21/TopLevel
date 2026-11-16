package com.toplevel.app.data

import java.text.Normalizer

/** A typical dish / portion with approximate macros (offline, instant). */
data class Dish(
    val name: String,
    val portion: String,
    val kcal: Int, val protein: Int, val carbs: Int, val fat: Int,
    val aliases: List<String> = emptyList(),
) {
    fun macros() = Repo.Macros("$name ($portion)", kcal, protein, carbs, fat)
}

/** Common Austrian/German dishes and staples. Values are typical averages. */
object LocalFoods {
    val dishes = listOf(
        Dish("Hühnerdöner", "1 Stück", 620, 42, 58, 22, listOf("hühner döner", "chicken döner", "chicken kebab", "döner huhn", "hendldöner")),
        Dish("Döner Kebab (Kalb/Rind)", "1 Stück", 720, 36, 60, 36, listOf("döner", "kebab", "kebap", "kalbsdöner")),
        Dish("Hühner-Dürüm", "1 Stück", 680, 44, 66, 24, listOf("dürüm huhn", "chicken dürüm", "dürüm", "yufka")),
        Dish("Döner-Box mit Pommes", "1 Box", 900, 40, 70, 50, listOf("dönerbox", "döner box", "kebab box")),
        Dish("Falafel-Dürüm", "1 Stück", 650, 18, 82, 27, listOf("falafel", "falafel wrap")),
        Dish("Lahmacun mit Salat", "1 Stück", 450, 20, 60, 14, listOf("lahmacun", "türkische pizza")),
        Dish("Wiener Schnitzel mit Pommes", "1 Portion", 1050, 45, 90, 55, listOf("schnitzel", "wiener schnitzel", "schnitzel pommes")),
        Dish("Wiener Schnitzel mit Erdäpfelsalat", "1 Portion", 900, 42, 60, 52, listOf("schnitzel kartoffelsalat", "schnitzel erdäpfelsalat")),
        Dish("Schnitzelsemmel", "1 Stück", 560, 28, 55, 24, listOf("schnitzel semmel", "schnitzelbrot")),
        Dish("Leberkässemmel", "1 Stück", 520, 18, 44, 30, listOf("leberkäse semmel", "leberkas", "leberkässemmel", "lkw")),
        Dish("Käsekrainer mit Senf & Brot", "1 Portion", 620, 22, 35, 44, listOf("käsekrainer", "kasekrainer", "würstel")),
        Dish("Bosna", "1 Stück", 560, 20, 45, 33, listOf("bosna", "bosner")),
        Dish("Gulasch mit Semmel", "1 Portion", 620, 36, 45, 30, listOf("gulasch", "rindsgulasch", "gulasch semmel")),
        Dish("Kaiserschmarrn", "1 Portion", 720, 16, 92, 30, listOf("schmarrn", "kaiserschmarrn")),
        Dish("Pizza Margherita", "ganze Pizza", 900, 36, 112, 32, listOf("pizza", "margherita")),
        Dish("Pizza Salami", "ganze Pizza", 1050, 42, 112, 46, listOf("pizza salami", "salamipizza")),
        Dish("Spaghetti Bolognese", "1 Teller", 650, 30, 82, 20, listOf("bolognese", "spaghetti", "nudeln bolognese")),
        Dish("Cheeseburger", "1 Stück", 300, 15, 32, 12, listOf("burger", "cheeseburger")),
        Dish("Pommes", "mittlere Portion", 340, 4, 43, 16, listOf("pommes", "fries", "pommes frites")),
        Dish("Sushi-Box", "8 Stück", 380, 14, 64, 7, listOf("sushi", "maki")),
        Dish("Bowl mit Huhn & Reis", "1 Bowl", 650, 45, 75, 16, listOf("poke bowl", "chicken bowl", "reis huhn", "rice bowl")),
        Dish("Hühnerbrust gebraten", "200 g", 330, 62, 0, 8, listOf("hühnerbrust", "huhn", "chicken breast", "hühnerfilet", "pute")),
        Dish("Reis gekocht", "200 g", 260, 5, 56, 1, listOf("reis", "rice")),
        Dish("Nudeln gekocht", "200 g", 300, 11, 58, 2, listOf("nudeln", "pasta")),
        Dish("Erdäpfel gekocht", "250 g", 190, 5, 42, 0, listOf("kartoffeln", "erdäpfel", "potatoes")),
        Dish("Haferflocken mit Milch", "60 g + 250 ml", 370, 18, 50, 11, listOf("haferflocken", "porridge", "oats", "müsli")),
        Dish("Skyr natur", "150 g", 95, 16, 6, 0, listOf("skyr")),
        Dish("Magertopfen", "250 g", 170, 30, 10, 1, listOf("topfen", "quark", "magerquark")),
        Dish("Eier", "2 Stück", 155, 13, 1, 11, listOf("ei", "eier", "eggs", "spiegelei", "rührei")),
        Dish("Semmel", "1 Stück", 140, 5, 28, 1, listOf("semmel", "brötchen", "kaisersemmel")),
        Dish("Vollkornbrot", "2 Scheiben", 210, 8, 38, 2, listOf("brot", "vollkornbrot", "schwarzbrot")),
        Dish("Proteinshake (Whey + Wasser)", "30 g", 120, 24, 3, 2, listOf("shake", "whey", "protein shake", "proteinshake")),
        Dish("Proteinriegel", "1 Riegel", 200, 20, 18, 7, listOf("riegel", "protein bar", "proteinriegel")),
        Dish("Banane", "1 Stück", 105, 1, 27, 0, listOf("banane", "banana")),
        Dish("Apfel", "1 Stück", 80, 0, 21, 0, listOf("apfel", "apple")),
        Dish("Nüsse gemischt", "30 g", 185, 6, 5, 16, listOf("nüsse", "mandeln", "nuts", "studentenfutter")),
        Dish("Avocado", "1/2 Stück", 160, 2, 2, 15, listOf("avocado")),
        Dish("Thunfisch (Dose, natur)", "1 Dose 150 g", 160, 36, 0, 1, listOf("thunfisch", "tuna")),
        Dish("Lachs gebraten", "150 g", 310, 32, 0, 20, listOf("lachs", "salmon")),
        Dish("Bier", "0,5 l", 210, 2, 16, 0, listOf("bier", "beer", "krügerl", "seidl")),
        Dish("Spritzer (weiß)", "0,25 l", 90, 0, 2, 0, listOf("spritzer", "weißer spritzer", "g'spritzter")),
        Dish("Melange", "1 Tasse", 90, 4, 7, 5, listOf("melange", "cappuccino", "kaffee mit milch")),
    )

    /** Lowercase, no accents/umlaut dots, no spaces or punctuation. */
    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .replace("ß", "ss").replace(Regex("[^a-z0-9]"), "")

    private fun tokens(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

    /** Dishes where every word of the query appears in the name or an alias. */
    fun search(query: String): List<Dish> {
        val qt = tokens(query)
        if (qt.isEmpty()) return emptyList()
        return dishes.map { d ->
            val keys = (listOf(d.name) + d.aliases).map { norm(it) }
            val score = keys.maxOf { k ->
                when {
                    k == norm(query) -> 3
                    qt.all { it in k } -> 2
                    qt.any { it.length >= 4 && it in k } -> 1
                    else -> 0
                }
            }
            d to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }.take(4)
    }
}
