@org.springframework.modulith.ApplicationModule(
        displayName = "customer",
        allowedDependencies = {
                "admin",
                "auth",
                "shared::audit",
                "shared::domain",
                "shared::exception",
                "shared::security"
        }
)
package com.numera.customer;
