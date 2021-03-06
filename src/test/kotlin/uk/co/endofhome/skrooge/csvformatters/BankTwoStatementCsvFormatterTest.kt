package uk.co.endofhome.skrooge.csvformatters

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

class BankTwoStatementCsvFormatterTest : CsvFormatterTest() {

    @Rule @JvmField
    val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val bankName = System.getenv("BANK_TWO")
    private val merchantFour = System.getenv("MERCHANT_FOUR")
    private val merchantFive = System.getenv("MERCHANT_FIVE")
    private val merchantSix = System.getenv("MERCHANT_SIX")

    // You will need csv files in your 'input' directory. as well
    // as environment variables for bankName and merchants set up
    // in order to run these tests.

    @Test
    fun `can format one-line statement`() {
        val formattedStatement = BankTwoStatementCsvFormatter(Paths.get("${bankName}_test_one_line.csv"))
        val expectedFormat =
                listOf(
                        "2017-11-13,$merchantSix,85.00"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format three-line statement`() {
        val formattedStatement = BankTwoStatementCsvFormatter(Paths.get("${bankName}_test_three_lines.csv"))
        val expectedFormat =
                listOf(
                        "2017-11-15,$merchantFour,-28.33",
                        "2017-11-15,$merchantFive,-28.33",
                        "2017-11-13,$merchantSix,85.00"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format full statement`() {
        val formattedStatement = BankTwoStatementCsvFormatter(Paths.get("${bankName}_test_full.csv"))

        approver.assertApproved(formattedStatement.joinToString(System.lineSeparator()))
    }
}