package com.example.tavi.voice

import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class SpeechGenerationCallbackTest {
    @Test fun nativeCallbackRetainsItsTypedBoxedSignatureAndHonorsCancellation() {
        val job = Job()
        val callback = SpeechGenerationCallback(job)
        val method = callback.javaClass.getMethod("invoke", FloatArray::class.java)
        assertEquals(Int::class.javaObjectType, method.returnType)
        assertEquals(1, callback.invoke(floatArrayOf(.1f)))
        job.cancel()
        assertEquals(0, callback.invoke(floatArrayOf(.1f)))
    }
}
