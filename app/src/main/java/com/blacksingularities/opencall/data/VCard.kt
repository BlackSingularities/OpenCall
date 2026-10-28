package com.blacksingularities.opencall.data

/**
 * vCard 3.0: zapis kontaktów do udostępniania i kopii zapasowej, odczyt wersji 2.1/3.0/4.0
 * (QUOTED-PRINTABLE, zawijane linie, CHARSET). Bez Androida – testowane jednostkowo.
 */
object VCard {
    fun write(d: ContactDraft): String = buildString {
        fun line(s: String) {
            // Linie dłuższe niż 75 znaków są zawijane (RFC 6350 §3.2).
            var rest = s
            var first = true
            while (rest.length > 75) {
                append(if (first) "" else " ").append(rest.take(75)).append("\r\n")
                rest = rest.drop(75)
                first = false
            }
            append(if (first) "" else " ").append(rest).append("\r\n")
        }
        line("BEGIN:VCARD")
        line("VERSION:3.0")
        line("N:" + listOf(d.family, d.given, d.middle, d.prefix, d.suffix).joinToString(";") { esc(it) })
        line("FN:" + esc(d.displayName.ifBlank { d.organization.ifBlank { d.phones.firstOrNull()?.value ?: "" } }))
        if (d.nickname.isNotBlank()) line("NICKNAME:" + esc(d.nickname))
        if (d.organization.isNotBlank()) line("ORG:" + esc(d.organization))
        if (d.title.isNotBlank()) line("TITLE:" + esc(d.title))
        d.phones.filter { it.value.isNotBlank() }.forEach { line("TEL;TYPE=${phoneType(it.type)}:" + it.value.trim()) }
        d.emails.filter { it.value.isNotBlank() }.forEach { line("EMAIL;TYPE=${emailType(it.type)}:" + esc(it.value.trim())) }
        d.addresses.filter { it.value.isNotBlank() }.forEach { line("ADR;TYPE=${if (it.type == 2) "WORK" else "HOME"}:;;" + esc(it.value.trim()) + ";;;;") }
        d.websites.filter { it.isNotBlank() }.forEach { line("URL:" + esc(it.trim())) }
        if (d.birthday.isNotBlank()) line("BDAY:" + d.birthday.trim())
        if (d.note.isNotBlank()) line("NOTE:" + esc(d.note))
        line("END:VCARD")
    }

    fun writeAll(list: List<ContactDraft>): String = list.joinToString("") { write(it) }

    fun parse(text: String): List<ContactDraft> {
        val out = ArrayList<ContactDraft>()
        var cur: ContactDraft? = null
        var fn = ""
        for (raw in unfold(text)) {
            val colon = findColon(raw)
            if (colon < 0) continue
            val head = raw.substring(0, colon)
            var value = raw.substring(colon + 1)
            val parts = head.split(';')
            val name = parts[0].substringAfter('.').uppercase()
            val params = parts.drop(1).map { it.uppercase() }
            if (params.any { it == "ENCODING=QUOTED-PRINTABLE" || it == "QUOTED-PRINTABLE" }) {
                val charset = params.firstOrNull { it.startsWith("CHARSET=") }?.substringAfter('=') ?: "UTF-8"
                value = decodeQp(value, charset)
            }
            val types = params.flatMap { p -> p.removePrefix("TYPE=").split(',') }
            when (name) {
                "BEGIN" -> if (value.equals("VCARD", true)) {
                    cur = ContactDraft(phones = emptyList())
                    fn = ""
                }
                "END" -> if (value.equals("VCARD", true)) {
                    cur?.let { c ->
                        val withName = if (c.displayName.isBlank() && fn.isNotBlank()) c.copy(given = fn) else c
                        if (!withName.isEmpty) out.add(withName)
                    }
                    cur = null
                }
                "N" -> cur = cur?.let { c ->
                    val n = splitEsc(value, ';')
                    c.copy(
                        family = n.getOrElse(0) { "" },
                        given = n.getOrElse(1) { "" },
                        middle = n.getOrElse(2) { "" },
                        prefix = n.getOrElse(3) { "" },
                        suffix = n.getOrElse(4) { "" },
                    )
                }
                "FN" -> fn = unesc(value)
                "NICKNAME" -> cur = cur?.copy(nickname = unesc(value))
                "ORG" -> cur = cur?.copy(organization = splitEsc(value, ';').filter { it.isNotBlank() }.joinToString(", "))
                "TITLE" -> cur = cur?.copy(title = unesc(value))
                "TEL" -> cur = cur?.let { c -> c.copy(phones = c.phones + DraftField(value.removePrefix("tel:").trim(), phoneTypeOf(types))) }
                "EMAIL" -> cur = cur?.let { c -> c.copy(emails = c.emails + DraftField(unesc(value).trim(), emailTypeOf(types))) }
                "ADR" -> cur = cur?.let { c ->
                    val a = splitEsc(value, ';').filter { it.isNotBlank() }.joinToString(", ")
                    if (a.isBlank()) c else c.copy(addresses = c.addresses + DraftField(a, if ("WORK" in types) 2 else 1))
                }
                "URL" -> cur = cur?.let { c -> c.copy(websites = c.websites + unesc(value)) }
                "BDAY" -> cur = cur?.copy(birthday = value.trim())
                "NOTE" -> cur = cur?.copy(note = unesc(value))
            }
        }
        return out
    }

    // Typy numerów jak w ContactsContract.CommonDataKinds.Phone.
    private fun phoneType(t: Int) = when (t) {
        1 -> "HOME"
        2 -> "CELL"
        3 -> "WORK"
        4 -> "WORK,FAX"
        5 -> "HOME,FAX"
        6 -> "PAGER"
        else -> "VOICE"
    }

    private fun phoneTypeOf(types: List<String>) = when {
        "FAX" in types && "WORK" in types -> 4
        "FAX" in types -> 5
        "CELL" in types || "MOBILE" in types -> 2
        "WORK" in types -> 3
        "HOME" in types -> 1
        "PAGER" in types -> 6
        else -> 7
    }

    private fun emailType(t: Int) = when (t) {
        1 -> "HOME"
        2 -> "WORK"
        else -> "INTERNET"
    }

    private fun emailTypeOf(types: List<String>) = when {
        "WORK" in types -> 2
        "HOME" in types -> 1
        else -> 3
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private fun unesc(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun splitEsc(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                cur.append(c).append(s[i + 1])
                i += 2
                continue
            }
            if (c == sep) {
                out.add(unesc(cur.toString()))
                cur.clear()
            } else {
                cur.append(c)
            }
            i++
        }
        out.add(unesc(cur.toString()))
        return out
    }

    /** Dwukropek oddzielający nazwę od wartości – poza cudzysłowem parametrów. */
    private fun findColon(s: String): Int {
        var quoted = false
        s.forEachIndexed { i, c ->
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) return i
        }
        return -1
    }

    /** Łączy linie zawinięte (spacja/tab na początku) i miękkie podziały QUOTED-PRINTABLE („=” na końcu). */
    private fun unfold(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>()
        for (l in lines) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + l.substring(1)
            } else if (out.isNotEmpty() && out.last().endsWith("=") && out.last().uppercase().contains("QUOTED-PRINTABLE")) {
                out[out.size - 1] = out.last().dropLast(1) + l
            } else if (l.isNotBlank()) {
                out.add(l)
            }
        }
        return out
    }

    private fun decodeQp(s: String, charset: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 3 <= s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    bytes.write(v)
                    i += 3
                    continue
                }
            }
            bytes.write(c.code and 0xFF)
            i++
        }
        return runCatching { String(bytes.toByteArray(), charset(charset)) }.getOrElse { String(bytes.toByteArray()) }
    }
}
