package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.TEMPORARY_REDIRECT
import org.http4k.core.body.form
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.categoryMapping
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statements
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.StubbedMappingWriter
import java.nio.file.Paths

class CategoryMappingTest {
    private val categoryMappings = mutableListOf("Edgeworld Records,Fun,Tom fun budget")
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val mappingWriter = StubbedMappingWriter()
    private val originalRequestBody = "2017;February;Test;[src/test/resources/2017-02_Someone_one-known-merchant.csv]"

    private val skrooge = Skrooge(categories, mappingWriter, budgetDirectory = Paths.get("src/test/resources/budgets/")).routes

    @Test
    fun `POST to category-mapping endpoint with empty new-mapping field returns HTTP Bad Request`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "")
                .form("remaining-vendors", "")
                .form("originalRequestBody", originalRequestBody)

        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to category-mapping endpoint with non-CSV content returns HTTP Bad Request`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Casbah Records;Established 1967 in our minds")
                .form("remaining-vendors", "")
                .form("originalRequestBody", originalRequestBody)

        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to category-mapping endpoint with good CSV content returns HTTP OK and writes new mapping`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Casbah Records,Fun,Tom fun budget")
                .form("remaining-vendors", "")
                .form("originalRequestBody", originalRequestBody)

        assertThat(skrooge(request).status, equalTo(TEMPORARY_REDIRECT))
        assertThat(mappingWriter.read().last(), equalTo("Casbah Records,Fun,Tom fun budget"))
    }

    @Test
    fun `succesful POST to category-mapping redirects back to continue categorisation if necessary`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "DIY Space for London,Fun,Tom fun budget")
                .form("remaining-vendors", "Another vendor")
                .form("originalRequestBody", originalRequestBody)

        val followedResponse = with(RedirectHelper(skrooge)) { request.handleAndfollowRedirect() }

        assertThat(mappingWriter.read().last(), equalTo("DIY Space for London,Fun,Tom fun budget"))
        assertThat(followedResponse.status, equalTo(OK))
        assertThat(followedResponse.bodyString(), containsSubstring("You need to categorise some merchants."))
        assertThat(followedResponse.bodyString(), containsSubstring("<h3>Another vendor</h3>"))
    }

    @Test
    fun `when all categories have been mapped a monthly report is available for review`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Last new mapping,Fun,Tom fun budget")
                .form("remaining-vendors", "")
                .form("originalRequestBody", originalRequestBody)

        val response = skrooge(request)

        assertThat(response.status, equalTo(TEMPORARY_REDIRECT))
        assertThat(response.header("Method")!!, equalTo("POST"))
        assertTrue(response.header("Location")!!.endsWith(statements))
        assertThat(response.body, equalTo(Body(originalRequestBody)))
    }
}