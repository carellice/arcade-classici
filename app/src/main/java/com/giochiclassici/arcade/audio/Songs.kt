package com.giochiclassici.arcade.audio

/**
 * Le musiche dell'app. Ogni token è un ottavo: nota ("A4"), "-" tiene, "." pausa.
 * Basso e arpeggio sono generati automaticamente dagli accordi (uno per battuta).
 */
object Songs {

    val menu: Song by lazy {
        build(
            bpm = 112,
            chords = "Am F C G Am F C G",
            lead = """
                A4 - C5 - E5 - D5 C5 | D5 - - C5 A4 - - - | G4 - C5 - E5 - G5 - | F5 - E5 - D5 - - -
                A5 - G5 - E5 - C5 -  | D5 - E5 - F5 - E5 D5 | E5 - - C5 G4 - C5 - | B4 - - - D5 - B4 -
            """,
            drums = "k h s h k k s h",
        )
    }

    val snake: Song by lazy {
        build(
            bpm = 150,
            chords = "C Am F G C Am F G",
            lead = """
                C5 E5 G5 E5 C6 - G5 - | A5 - G5 E5 C5 - E5 - | F5 - A5 - G5 F5 E5 D5 | D5 - G5 - B4 - D5 -
                E5 - G5 - C6 - B5 C6  | A5 - E5 - C5 - D5 E5 | F5 E5 D5 C5 A4 - C5 - | D5 - - - G4 - . .
            """,
            drums = "x h s h x h s o",
        )
    }

    val mines: Song by lazy {
        build(
            bpm = 100,
            chords = "Dm C Bb C Dm C Bb C",
            lead = """
                D5 - F5 - A5 - - G5 | E5 - - - C5 - D5 E5 | F5 - D5 - Bb4 - D5 - | C5 - E5 - G5 - - -
                A5 - G5 F5 E5 - D5 - | E5 - G5 - - E5 C5 - | D5 - F5 - Bb5 - A5 G5 | E5 - - - . . . .
            """,
            drums = "k . h . s . h h",
            leadWave = Wave.TRIANGLE,
            leadVolume = 0.28f,
        )
    }

    val breakout: Song by lazy {
        build(
            bpm = 140,
            chords = "Em C G D Em C G D",
            lead = """
                E5 - G5 - B5 - A5 G5 | E5 - - - C5 D5 E5 - | D5 - G5 - B4 - D5 G5 | F#5 - - - A5 - F#5 -
                B5 - A5 - G5 - F#5 G5 | E5 - - G5 E5 - C5 - | D5 - B4 - D5 - G5 - | F#5 - A5 - D6 - - -
            """,
            drums = "x h s h x x s h",
        )
    }

    val invaders: Song by lazy {
        build(
            bpm = 128,
            chords = "Am Am F G Am Am Dm E",
            lead = """
                A4 - - E5 - - A5 -  | G5 - E5 - C5 - D5 E5 | F5 - - C5 - - A4 - | B4 - D5 - G5 - - -
                A5 - G5 - E5 - A5 - | C6 - B5 - A5 - E5 -  | F5 - E5 - D5 - F5 - | E5 - - - G#5 - B5 -
            """,
            drums = "k h s h k h s h",
            leadVolume = 0.12f,
        )
    }

    private fun parse(s: String): IntArray =
        s.split(' ', '|', '\n', '\t').filter { it.isNotBlank() }.map {
            when (it) {
                "." -> REST
                "-" -> HOLD
                else -> Notes.midi(it)
            }
        }.toIntArray()

    private fun chordTones(name: String): IntArray {
        val minor = name.endsWith("m")
        val root = if (minor) name.dropLast(1) else name
        val r = Notes.midi(root + "4")
        return intArrayOf(r, r + (if (minor) 3 else 4), r + 7)
    }

    private fun build(
        bpm: Int,
        chords: String,
        lead: String,
        drums: String,
        leadWave: Wave = Wave.SQUARE,
        leadVolume: Float = 0.15f,
    ): Song {
        val chordList = chords.split(' ').filter { it.isNotBlank() }
        val bass = ArrayList<Int>()
        val arp = ArrayList<Int>()
        val arpPattern = intArrayOf(0, 1, 2, 3, 2, 1, 0, 1)
        for (c in chordList) {
            val t = chordTones(c)
            var root = t[0] - 24
            if (root < 40) root += 12
            repeat(8) { i -> bass += if (i % 2 == 0) root else root + 12 }
            val tones = intArrayOf(t[0], t[1], t[2], t[0] + 12)
            for (i in arpPattern) arp += tones[i]
        }
        return Song(
            bpm = bpm,
            lead = parse(lead),
            arp = arp.toIntArray(),
            bass = bass.toIntArray(),
            drums = drums.replace(" ", ""),
            leadWave = leadWave,
            leadDuty = if (leadWave == Wave.SQUARE) 0.25f else 0.5f,
            leadVolume = leadVolume,
        )
    }
}
