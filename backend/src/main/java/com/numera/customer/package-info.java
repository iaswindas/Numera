@org.springframework.modulith.ApplicationModule(
        displayName = "customer",
        allowedDependencies = {
                "shared::audit",
                "shared::domain",
                "shared::exception"
        }
)
package com.numera.customer;
