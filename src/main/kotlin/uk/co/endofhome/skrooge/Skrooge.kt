package uk.co.endofhome.skrooge

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.filter.DebuggingFilters
import org.http4k.format.Gson
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.view
import uk.co.endofhome.skrooge.categories.AnnualBudgets
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.CategoryMappings
import uk.co.endofhome.skrooge.categories.CategoryReporter
import uk.co.endofhome.skrooge.categories.FileSystemMappingWriter
import uk.co.endofhome.skrooge.categories.MappingWriter
import uk.co.endofhome.skrooge.decisions.DecisionReaderWriter
import uk.co.endofhome.skrooge.decisions.FileSystemDecisionReaderReaderWriter
import uk.co.endofhome.skrooge.reportcategorisations.ReportCategorisations
import uk.co.endofhome.skrooge.reports.AnnualReporter
import uk.co.endofhome.skrooge.reports.BarCharts
import uk.co.endofhome.skrooge.reports.MonthlyReporter
import uk.co.endofhome.skrooge.statements.Statements
import uk.co.endofhome.skrooge.unknownmerchant.UnknownMerchantHandler
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale.UK

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
    val app = Skrooge().routes()
                       .withFilter(DebuggingFilters.PrintRequestAndResponse())
    app.asServer(Jetty(port)).start()
    println("Skrooge has started on http://localhost:$port")
}

class Skrooge(private val categories: Categories = Categories(),
              private val mappingWriter: MappingWriter = FileSystemMappingWriter(),
              private val decisionReaderWriter: DecisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories),
              budgetDirectory: Path = Paths.get("input/budgets/")) {

    private val categoryMappings = categories.categoryMappings
    private val gson = Gson
    private val renderer = HandlebarsTemplates().HotReload("src/main/resources")
    private val publicDirectory = static(ResourceLoader.Directory("public"))
    private val annualBudgets = AnnualBudgets.from(budgetDirectory)
    private val categoryReporter = CategoryReporter(categories.all(), annualBudgets)
    private val annualReporter = AnnualReporter(gson, decisionReaderWriter, categoryReporter)
    private val monthlyReporter = MonthlyReporter(gson, decisionReaderWriter, categoryReporter)

    fun routes() = routes(
            "/public" bind publicDirectory,
            "/" bind GET to { _ -> index() },
            "/statements" bind POST to { request -> Statements(categories).upload(request, renderer) },
            "/unknown-merchant" bind GET to { request -> UnknownMerchantHandler(renderer, categories.all()).handle(request) },
            "category-mapping" bind POST to { request -> CategoryMappings(categoryMappings, mappingWriter).addCategoryMapping(request) },
            "reports/categorisations" bind POST to { request -> ReportCategorisations(decisionReaderWriter, categories.all()).confirm(request) },
            "annual-report/json" bind GET to { request -> annualReporter(request) },
            "monthly-report/json" bind GET to { request -> monthlyReporter(request) },
            "web" bind GET to { request -> BarCharts(request, renderer) }
    )

    private fun index(): Response {
        val years = listOf(DisplayYear.of("2017"), DisplayYear.of("2018"), DisplayYear.of("2019"))
        val months = Month.values().map { DisplayMonth.of(it) }
        val users = listOf(System.getenv("PARTICIPANT_TWO"), System.getenv("PARTICIPANT_ONE"), System.getenv("PARTICIPANT_THREE"))
        val statementTypes = listOf(
            StatementType(System.getenv("BANK_FIVE"), System.getenv("BANK_FIVE_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_SIX"), System.getenv("BANK_SIX_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_ONE"), System.getenv("BANK_ONE_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_THREE"), System.getenv("BANK_THREE_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_FOUR"), System.getenv("BANK_FOUR_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_TWO"), System.getenv("BANK_TWO_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_SEVEN"), System.getenv("BANK_SEVEN_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_EIGHT"), System.getenv("BANK_EIGHT_DISPLAY_NAME"))
        )
        val index = Index(years, months, users, statementTypes)
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        return Response(Status.OK).with(view of index)
    }

    data class StatementType(val id: String, val displayName: String)
}

data class DisplayYear(val name: String, val selector: String) {
    companion object {
        fun of(year: String): DisplayYear = DisplayYear(year, selectedString(year))

        private fun selectedString(year: String): String {
            val selectedYear = LocalDate.now().year.toString()
            return when (year) {
                selectedYear -> " selected"
                else        -> ""
            }
        }
    }
}

data class DisplayMonth(val name: String, val selector: String) {
    companion object {
        fun of(month: Month): DisplayMonth =
                DisplayMonth(month.getDisplayName(TextStyle.FULL, UK), selectedString(month))

        private fun selectedString(month: Month): String {
            val selectedMonth = LocalDate.now().month
            return when (month) {
                selectedMonth -> " selected"
                else          -> ""
            }
        }
    }
}

data class Index(val years: List<DisplayYear>, val months: List<DisplayMonth>, val users: List<String>, val statementTypes: List<Skrooge.StatementType>) : ViewModel
