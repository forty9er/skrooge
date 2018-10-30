package uk.co.endofhome.skrooge.decisions

import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.FormField
import org.http4k.lens.Validator
import org.http4k.lens.webForm
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.index
import uk.co.endofhome.skrooge.statements.StatementData
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.statement
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
import java.time.LocalDate
import java.time.Month
import java.time.Year

class DecisionsHandler(private val decisionReaderWriter: DecisionReaderWriter, val categories: List<Category>) {
    companion object {
        const val decision = "decision"
    }

    operator fun invoke(request: Request): Response {
        val yearLens = FormField.required(yearName)
        val monthLens = FormField.required(monthName)
        val userLens = FormField.required(userName)
        val statementLens = FormField.required(statement)
        val decisionLens = FormField.multi.required(decision)
        val webForm = Body.webForm(
            Validator.Feedback,
            yearLens,
            monthLens,
            userLens,
            statementLens,
            decisionLens
        )
        val form = webForm.toLens().extract(request)

        return if (form.errors.isEmpty()) {
            val year = yearLens.extract(form)
            val month = monthLens.extract(form)
            val user = userLens.extract(form)
            val statement = statementLens.extract(form)

            val decisions: List<Decision> = decisionLens.extract(form).map {
                val decisionLine = it.split(",")
                Decision(
                    Line(
                        date = reformatDate(presentationFormattedDate = decisionLine[0]),
                        merchant = decisionLine[1],
                        amount = decisionLine[2].toDouble()
                    ),
                    Category(
                        title = decisionLine[3],
                        subcategories = categories.find { it.title == decisionLine[3] }!!.subcategories
                    ),
                    SubCategory(name = decisionLine[4])
                )
            }

            val statementData = StatementData(Year.parse(year), Month.valueOf(month.toUpperCase()), user, statement)
            decisionReaderWriter.write(statementData, decisions)

            Response(Status.SEE_OTHER).header("Location", index)
        } else {
            val fieldsWithErrors = form.errors.map { it.meta.name }
            val osNewline = System.lineSeparator()

            Response(Status.BAD_REQUEST).body(
                "Form fields were missing:$osNewline${fieldsWithErrors.joinToString(osNewline)}"
            )
        }
    }

    private fun reformatDate(presentationFormattedDate: String): LocalDate {
        val dateParts = presentationFormattedDate.split("/")
        val day = Integer.valueOf(dateParts[0])
        val month = Month.of(Integer.valueOf(dateParts[1]))
        val year = Integer.valueOf(dateParts[2])
        return LocalDate.of(year, month, day)
    }
}
