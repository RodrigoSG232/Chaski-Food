package com.chaskifood.app.core.firebase

import org.junit.Assert.*
import org.junit.Test

class FoodApiEnvironmentTest {
    private val cloud = "https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/"
    private fun local(host: String) = "http://$host:5001/demo-chaski-hu15/southamerica-west1/foodApi/api/v1/"
    @Test fun `explicit LOCAL and CONECTADO configurations are supported`() {
        for (host in listOf("127.0.0.1", "10.0.2.2"))
            assertTrue(FoodApiEnvironment.isConfigured("LOCAL", "demo-chaski-hu15", "", local(host), host))
        assertTrue(FoodApiEnvironment.isConfigured("CONECTADO", "chaski-food-9adba", "chaski-food-9adba", cloud, ""))
    }
    @Test fun `mixed environments missing configuration and Release stay closed`() {
        assertFalse(FoodApiEnvironment.isConfigured("LOCAL", "chaski-food-9adba", "", local("10.0.2.2"), "10.0.2.2"))
        assertFalse(FoodApiEnvironment.isConfigured("LOCAL", "demo-chaski-hu15", "", cloud, "10.0.2.2"))
        assertFalse(FoodApiEnvironment.isConfigured("CONECTADO", "demo-chaski-hu15", "chaski-food-9adba", cloud, ""))
        assertFalse(FoodApiEnvironment.isConfigured("CONECTADO", "chaski-food-9adba", "", cloud, ""))
        assertFalse(FoodApiEnvironment.isConfigured("CONECTADO", "chaski-food-9adba", "chaski-food-9adba", local("10.0.2.2"), ""))
        assertFalse(FoodApiEnvironment.isConfigured("CONECTADO", "chaski-food-9adba", "chaski-food-9adba", cloud, "10.0.2.2"))
        assertFalse(FoodApiEnvironment.isConfigured("CONECTADO", "chaski-food-9adba", "", "https://unconfigured.invalid/", ""))
        for (url in listOf(cloud.replace("https:", "http:"), cloud + "evil", cloud.replace("a.run.app", "a.run.app.evil.test")))
            assertFalse(FoodApiEnvironment.isConfigured("CONECTADO", "chaski-food-9adba", "chaski-food-9adba", url, ""))
        assertFalse(FoodApiEnvironment.isConfigured("UNKNOWN", null, "", cloud, ""))
    }
}
