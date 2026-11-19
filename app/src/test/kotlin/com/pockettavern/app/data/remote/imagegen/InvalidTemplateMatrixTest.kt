package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ImageGenBackendType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** One independent JUnit case per backend/input pair (8 x 12 = 96). */
@RunWith(Parameterized::class)
class InvalidTemplateMatrixTest(
    private val backend: ImageGenBackendType,
    private val input: String
) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} rejects malformed input")
        fun cases(): Collection<Array<Any>> {
            val invalid = listOf(
                "", " ", "\n\t", "{}", "[]", "null", "true", "42",
                "\"text\"", "{invalid json", "{\"broken\":", "[1,2,3]"
            )
            return ImageGenBackendType.entries.flatMap { backend ->
                invalid.map { arrayOf<Any>(backend, it) }
            }
        }
    }

    @Test fun malformedOrEmptyTemplateIsRejected() {
        val error = WorkflowImportValidator.validate(input, backend)
        assertNotNull("Unexpected acceptance for ${backend.displayName}: $input", error)
        assertTrue("Backend missing from message: $error", error!!.contains(backend.displayName))
    }
}
