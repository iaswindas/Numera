package com.numera.shared.util

import java.math.BigDecimal

fun Number.bd(): BigDecimal = BigDecimal(this.toString())