package com.blacksingularities.opencall.data

import java.util.Calendar

/** Czysto tekstowe operacje na numerach i nazwach – bez Androida, więc testowane jednostkowo. */
object PhoneText {
    private val t9 = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2',
        'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4',
        'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6',
        'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8',
        'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
    )

    private val folds = mapOf(
        'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n', 'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z',
        'ä' to 'a', 'ö' to 'o', 'ü' to 'u', 'ß' to 's', 'é' to 'e', 'è' to 'e', 'ê' to 'e', 'á' to 'a', 'à' to 'a',
        'í' to 'i', 'ì' to 'i', 'ú' to 'u', 'ù' to 'u', 'č' to 'c', 'š' to 's', 'ž' to 'z', 'ř' to 'r', 'ý' to 'y',
        'ñ' to 'n', 'ç' to 'c', 'ě' to 'e', 'ů' to 'u', 'å' to 'a', 'ø' to 'o', 'æ' to 'a',
    )

    /** Małe litery bez znaków diakrytycznych: „Łukasz Żółw” → „lukasz zolw”. */
    fun fold(s: String): String = buildString(s.length) {
        for (c in s.lowercase()) append(folds[c] ?: c)
    }

    /** Nazwa jako cyfry klawiatury; słowa rozdzielone spacją – do dopasowania początków słów. */
    fun toT9(name: String): String = buildString {
        for (c in fold(name)) {
            when {
                c in t9 -> append(t9[c])
                c.isDigit() -> append(c)
                c == ' ' || c == '-' || c == '.' -> if (isNotEmpty() && last() != ' ') append(' ')
            }
        }
    }.trim()

    /** Same cyfry numeru (z wiodącym „+”, jeśli był). */
    fun digits(number: String): String {
        val plus = number.trimStart().startsWith("+")
        val d = number.filter { it.isDigit() }
        return if (plus) "+$d" else d
    }

    /**
     * Klucz porównania numerów: ostatnie 9 cyfr. Dzięki temu „+48 600 100 200”, „0048600100200”
     * i „600-100-200” to ten sam numer – tak jak robi to większość dialerów.
     */
    fun key(number: String): String {
        val d = number.filter { it.isDigit() }
        return if (d.length > 9) d.takeLast(9) else d
    }

    fun sameNumber(a: String, b: String): Boolean {
        val ka = key(a)
        return ka.isNotEmpty() && ka == key(b)
    }

    /**
     * Wynik dopasowania T9 nazwy do wpisanych cyfr: 3 – początek nazwy, 2 – początek kolejnego
     * słowa, 1 – inicjały („jk” → Jan Kowalski), 0 – brak.
     */
    fun t9Score(t9Name: String, query: String): Int {
        if (query.isEmpty() || t9Name.isEmpty()) return 0
        val compact = t9Name.replace(" ", "")
        if (compact.startsWith(query)) return 3
        val words = t9Name.split(' ')
        if (words.drop(1).any { it.startsWith(query) }) return 2
        // Kolejne słowa od początku: „52 7” → „Jan Kowalski” jako 5+7 itp.
        val initials = words.mapNotNull { it.firstOrNull() }.joinToString("")
        if (query.length >= 2 && initials.startsWith(query)) return 1
        return 0
    }

    /** Czy wpisane cyfry występują w numerze (bez względu na spacje, myślniki i prefiks). */
    fun numberMatches(number: String, query: String): Boolean {
        val q = query.filter { it.isDigit() }
        if (q.isEmpty()) return false
        return number.filter { it.isDigit() }.contains(q)
    }

    /** Inicjały do awatara: „Jan Kowalski” → „JK”, „+48 600…” → „#”. */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.first().isLetter() }
        return when {
            words.isEmpty() -> "#"
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    /** Litera indeksu alfabetycznego: pierwsza litera bez diakrytyków albo „#”. */
    fun indexLetter(name: String): Char {
        val c = name.trim().firstOrNull()?.uppercaseChar() ?: return '#'
        if (!c.isLetter()) return '#'
        // Polskie litery mają własne sekcje jak w książce telefonicznej (Ł po L, Ś po S…).
        return c
    }

    /** Czas rozmowy: „0:42”, „12:05”, „1:02:33”. */
    fun duration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Czas rozmowy słownie, krótko: „42 s”, „3 min”, „1 h 5 min”. */
    fun durationShort(sec: Long, h: String = "h", min: String = "min", s: String = "s"): String = when {
        sec < 60 -> "$sec $s"
        sec < 3600 -> "${sec / 60} $min"
        else -> "${sec / 3600} $h ${(sec % 3600) / 60} $min"
    }

    /** Grupuje kolejne połączenia z tym samym numerem z tego samego dnia. */
    fun group(entries: List<CallEntry>, enabled: Boolean = true): List<CallGroup> {
        if (!enabled) return entries.map { CallGroup(listOf(it)) }
        val out = ArrayList<CallGroup>()
        var cur = ArrayList<CallEntry>()
        val cal = Calendar.getInstance()
        fun day(t: Long): Int {
            cal.timeInMillis = t
            return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        }
        for (e in entries) {
            val last = cur.lastOrNull()
            val sameKind = last != null && (last.kind == CallKind.MISSED) == (e.kind == CallKind.MISSED)
            if (last != null && sameKind && day(last.date) == day(e.date) &&
                (sameNumber(last.number, e.number) || (last.hidden && e.hidden))
            ) {
                cur.add(e)
            } else {
                if (cur.isNotEmpty()) out.add(CallGroup(cur))
                cur = arrayListOf(e)
            }
        }
        if (cur.isNotEmpty()) out.add(CallGroup(cur))
        return out
    }

    /** Czy numer pasuje do reguły blokady: prefiks („0700”, „+48 22”) albo wzorzec z „*”. */
    fun matchesRule(number: String, rule: String): Boolean {
        val r = rule.trim()
        if (r.isEmpty()) return false
        val n = digits(number)
        if (r.contains('*')) {
            val regex = Regex("^" + r.filter { it.isDigit() || it == '*' || it == '+' }
                .replace("+", "\\+").replace("*", "\\d*") + "$")
            return regex.matches(n) || regex.matches(n.removePrefix("+"))
        }
        val rd = digits(r)
        if (rd.isEmpty()) return false
        if (n.startsWith(rd)) return true
        // „0700” ma pasować też do „+48 700…”, a „+48 22” do „22…”.
        val national = nationalForm(n)
        return national.startsWith(nationalForm(rd))
    }

    /** Numer bez kodu kraju 48 / 0048 i bez wiodącego zera – uproszczony zapis krajowy. */
    private fun nationalForm(d: String): String {
        var x = d.removePrefix("+")
        if (x.startsWith("0048")) x = x.drop(4) else if (d.startsWith("+48")) x = x.drop(2)
        return x.trimStart('0')
    }

    /** Czytelny zapis polskiego numeru: „600100200” → „600 100 200”, „+48600100200” → „+48 600 100 200”. */
    fun pretty(number: String): String {
        val d = digits(number)
        val plain = d.removePrefix("+")
        return when {
            d.startsWith("+48") && plain.length == 11 -> "+48 " + plain.drop(2).chunked(3).joinToString(" ")
            !d.startsWith("+") && plain.length == 9 -> plain.chunked(3).joinToString(" ")
            else -> number.trim()
        }
    }
}
