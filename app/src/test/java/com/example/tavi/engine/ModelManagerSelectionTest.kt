package com.example.tavi.engine

import android.app.ActivityManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelManagerSelectionTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private val memoryPrefs = HashMap<String, Any>()

    @Before
    fun setUp() {
        tempDir = File.createTempFile("model_sel_test_", "_dir").apply {
            delete()
            mkdirs()
        }

        val mockSharedPreferences = object : SharedPreferences {
            override fun getAll(): MutableMap<String, *> = memoryPrefs
            override fun getString(key: String?, defValue: String?): String? = memoryPrefs[key] as? String ?: defValue
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
            override fun getInt(key: String?, defValue: Int): Int = memoryPrefs[key] as? Int ?: defValue
            override fun getLong(key: String?, defValue: Long): Long = memoryPrefs[key] as? Long ?: defValue
            override fun getFloat(key: String?, defValue: Float): Float = memoryPrefs[key] as? Float ?: defValue
            override fun getBoolean(key: String?, defValue: Boolean): Boolean = memoryPrefs[key] as? Boolean ?: defValue
            override fun contains(key: String?): Boolean = memoryPrefs.containsKey(key)
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                private val temp = HashMap<String, Any?>()
                override fun putString(key: String?, value: String?): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
                override fun putInt(key: String?, value: Int): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun putLong(key: String?, value: Long): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun remove(key: String?): SharedPreferences.Editor { temp.remove(key); return this }
                override fun clear(): SharedPreferences.Editor { temp.clear(); return this }
                override fun commit(): Boolean {
                    temp.forEach { (k, v) -> if (v != null) memoryPrefs[k] = v else memoryPrefs.remove(k) }
                    return true
                }
                override fun apply() { commit() }
            }
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }

        mockContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = tempDir
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = mockSharedPreferences
            override fun registerComponentCallbacks(callback: ComponentCallbacks?) {}
            override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {}
            override fun getSystemService(name: String): Any? = null
            override fun getApplicationContext(): Context = this
        }
    }

    @Test
    fun testCatalogHasBothModels() {
        val modelManager = ModelManager(mockContext)
        val models = modelManager.getAvailableModels()

        assertEquals("Expected 2 catalog models", 2, models.size)
        val e2b = models.find { it.id == ModelManager.MODEL_GEMMA_4_E2B }
        val g3 = models.find { it.id == ModelManager.MODEL_GEMMA_3_1B }

        assertNotNull("Gemma 4 E2B should be present", e2b)
        assertNotNull("Gemma 3 1B should be present", g3)

        assertEquals(6144, e2b?.ramRequiredMb)
        assertEquals(2048, g3?.ramRequiredMb)
    }

    @Test
    fun testSelectModelPersists() {
        val modelManager = ModelManager(mockContext)
        modelManager.selectModel(ModelManager.MODEL_GEMMA_4_E2B)
        assertEquals(ModelManager.MODEL_GEMMA_4_E2B, modelManager.activeModelId.value)

        modelManager.selectModel(ModelManager.MODEL_GEMMA_3_1B)
        assertEquals(ModelManager.MODEL_GEMMA_3_1B, modelManager.activeModelId.value)
    }

    @Test
    fun testFallbackChainModelFileResolution() {
        val modelManager = ModelManager(mockContext)
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()

        // When no file exists
        assertNotNull(modelManager.modelFile)

        // When only 3-1B is installed
        val file31B = File(modelsDir, ModelManager.MODEL_FILE_NAME_3_1B_LITERTLM).apply {
            writeText("dummy_3_1b_weights")
        }
        modelManager.selectModel(ModelManager.MODEL_GEMMA_4_E2B)
        // Should resolve fallback to 3-1B file when E2B is missing
        assertEquals(file31B.absolutePath, modelManager.modelFile.absolutePath)
    }
}
