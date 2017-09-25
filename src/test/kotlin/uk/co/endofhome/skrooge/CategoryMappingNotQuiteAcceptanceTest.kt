package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.body.form
import org.http4k.core.with
import org.http4k.hamkrest.hasStatus
import org.http4k.lens.Header
import org.junit.Test

class CategoryMappingNotQuiteAcceptanceTest {
    val categoryMappings = listOf("Edgeworld Records,Fun,Tom fun budget")
    val mappingWriter = MockMappingWriter()
    val skrooge = Skrooge(categoryMappings, mappingWriter).routes()

    @Test
    fun `POST to category-mapping endpoint with empty new-mapping field returns HTTP Bad Request`() {
        val request = Request(POST, "/category-mapping")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "")
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to category-mapping endpoint with non-CSV content returns HTTP Bad Request`() {
        val request = Request(POST, "/category-mapping")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Casbah Records;Established 1967 in our minds")
                .form("remaining-vendors", "")
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to category-mapping endpoint with good CSV content returns HTTP OK and writes new mapping`() {
        val request = Request(POST, "/category-mapping")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Casbah Records,Fun,Tom fun budget")
                .form("remaining-vendors", "")
        skrooge(request) shouldMatch hasStatus(OK)
        assertThat(mappingWriter.read().last(), equalTo("Casbah Records,Fun,Tom fun budget"))
    }
}