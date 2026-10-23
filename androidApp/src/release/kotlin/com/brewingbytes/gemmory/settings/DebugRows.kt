package com.brewingbytes.gemmory.settings

import androidx.compose.runtime.Composable

/** Release builds have no debug rows: the Clips screen (T47) exists only in debug builds. */
@Composable
fun DebugRows() = Unit
