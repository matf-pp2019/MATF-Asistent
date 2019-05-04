package scraper

import data.Course
import data.CourseDef
import data.Repository
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tornadofx.runAsync
import tornadofx.runLater
import java.time.DayOfWeek

data class CourseData(var startIndex: Int, var duration: Int, var title: String, var lecturer: String, var classroom: String) {
    fun eq(other: Any?): Boolean {
        if (other == null || other !is CourseData) return false
        return title == other.title && lecturer == other.lecturer && classroom == other.classroom
    }
}

fun fetchCourseListTask() = runAsync {
    val htmlSuffix = "form_025.html"
    Jsoup.connect("http://poincare.matf.bg.ac.rs/~kmiljan/raspored/sve/$htmlSuffix").get()
        .selectFirst("table")
        .selectFirst("tbody")
        .selectFirst("tr")
        .selectFirst("td:eq(1)")
        .selectFirst("table")
        .selectFirst("tbody")
        .children(startIndex = 2)
        .flatMap {
            // U ovom koraku, parsiraju se prve kolone svakog reda, tako da se asocira dan u nedelji sa listom
            val dayOfWeek = when (it.selectFirst("td").text()) {
                "Понедељак" -> DayOfWeek.MONDAY
                "Уторак" -> DayOfWeek.TUESDAY
                "Среда" -> DayOfWeek.WEDNESDAY
                "Четвртак" -> DayOfWeek.THURSDAY
                "Петак" -> DayOfWeek.FRIDAY

                else -> DayOfWeek.MONDAY
            }


            val courseData = coursesFromRawData(it.children(startIndex = 1))

            courseData.map { data -> Pair(dayOfWeek, data) }
        }
        .map {
            // Ovo će pretvoriti u klasu Course koju zapravo koristimo u projektu
            val courseData = it.second
            val type = when {
                courseData.title.contains("(вежбе)") -> {
                    courseData.title = courseData.title.replace("(вежбе)", "").trim()
                    Course.Type.EXERCISE
                }

                courseData.title.contains("(практикум)") -> {
                    courseData.title = courseData.title.replace("(практикум)", "").trim()
                    Course.Type.PRACTICUM
                }

                else -> Course.Type.LECTURE
            }


            val dayOfWeekString = when (it.first) {
                DayOfWeek.MONDAY -> "ponedeljak"
                DayOfWeek.TUESDAY -> "utorak"
                DayOfWeek.WEDNESDAY -> "sreda"
                DayOfWeek.THURSDAY -> "četvrak"
                DayOfWeek.FRIDAY -> "petak"

                else -> ""
            }

            val classroom = when {
                courseData.classroom.startsWith("ЈАГ") -> Course.Classroom.JAG
                courseData.classroom.startsWith("Н") -> Course.Classroom.N
                else -> Course.Classroom.TRG
            }

            Course(courseData.title, type, dayOfWeekString, classroom, courseData.startIndex, courseData.duration)
        }.forEach(::println)

}

private fun addCourseDefToRepository(vararg courseDefs: CourseDef) {
    runLater {
        Repository.allAvailableCourses.addAll(courseDefs)
    }
}

private fun coursesFromRawData(rawList: List<Element>): Set<CourseData> {

    var startIndex = 8

    return rawList.map {
        //Ovaj korak će dodeliti odgovarajuće početne časove i trajanja svakom od časova

        val duration = it.attr("colspan").toIntOrNull() ?: 1
        val returnVal = Triple(startIndex, duration, it)

        // Kada je neko predavanje 2h ili 3h, početni indeks treba dodatno da se poveća.
        startIndex += duration

        returnVal

    }.flatMap { triple: Triple<Int, Int, Element> ->
        // Ovo će razdvojiti časove na pojedinačne, ako se u isto vreme održava više časova na istom rasporedu
        when {

            // Ako je pronađena tabela, radi se o tabeli vežbi, ili praznoj tabeli
            triple.third.selectFirst("table") != null ->
                triple.third.selectFirst("table").selectFirst("tbody").select("tr").map {
                    Triple(triple.first, triple.second, it)
                }

            // Ako element ima `colspan` atribut, znači da je element predavanja
            triple.third.hasAttr("colspan") -> listOf(triple)

            // Nije prepoznat element
            else -> emptyList<Triple<Int, Int, Element>>()
        }
    }.filter {
        // Ovo će ukloniti "prazne" časove
        !it.third.text().isNullOrBlank()
    }.map {
        // Ovo će pretvoriti "sirovo" Triple<Int, Int, Element> u CourseData
        val innerHtml = it.third.selectFirst("small")?.html() ?: it.third.html()
        val (title, lecturer, classroom) = innerHtml.split("<br>").map { str -> str.trimIndent() }
        CourseData(it.first, it.second, title, lecturer, classroom)
    }.fold(mutableSetOf()) { acc, current ->
        // Ovo će spojiti više časova vežbi u jedan čas vežbi
        val existing = acc.find { it.eq(current) }

        if (existing != null) {
            existing.duration += current.duration
        } else {
            acc.add(current)
        }

        acc
    }
}

private fun Element.children(startIndex: Int, endIndex: Int = children().size): List<Element> {
    return children().subList(startIndex, endIndex)
}