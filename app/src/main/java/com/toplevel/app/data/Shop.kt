package com.toplevel.app.data

import java.net.URLEncoder

/**
 * Free price comparison: no AI, the app just opens the search of comparison sites and shops,
 * already sorted by price where the site supports it.
 */
object Shop {
    data class Site(val id: String, val name: String, val hint: String, val url: (String) -> String)

    private fun enc(q: String) = URLEncoder.encode(q.trim(), "UTF-8")

    /** [country] is the phone's country code (AT gets geizhals.at, everything else the .de sites). */
    fun sites(country: String): List<Site> {
        val at = country.equals("AT", true)
        return listOf(
            Site("geizhals", "Geizhals", "Compares 100s of shops, cheapest first") { q ->
                "https://geizhals.${if (at) "at" else "de"}/?fs=${enc(q)}&sort=p" },
            Site("idealo", "idealo", "Price comparison") { q ->
                "https://www.idealo.${if (at) "at" else "de"}/preisvergleich/MainSearchProductCategory.html?q=${enc(q)}" },
            Site("google", "Google Shopping", "Many shops at once") { q -> "https://www.google.com/search?tbm=shop&q=${enc(q)}" },
            Site("amazon", "Amazon", "Cheapest first") { q -> "https://www.amazon.de/s?k=${enc(q)}&s=price-asc-rank" },
            Site("ebay", "eBay", "Cheapest incl. shipping, new + used") { q -> "https://www.ebay.${if (at) "at" else "de"}/sch/i.html?_nkw=${enc(q)}&_sop=15" },
            Site("kleinanzeigen", "Kleinanzeigen", "Used, local") { q -> "https://www.kleinanzeigen.de/s-suchanfrage.html?keywords=${enc(q)}" },
        )
    }

    /** Newest first, no duplicates, max 8. */
    fun remember(recent: String, query: String): String =
        (listOf(query.trim()) + recent.lines()).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.take(8).joinToString("\n")
}
