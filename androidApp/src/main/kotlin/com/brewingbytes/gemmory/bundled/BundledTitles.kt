package com.brewingbytes.gemmory.bundled

import com.brewingbytes.gemmory.copy.Strings

/** Its title in [strings]' language: Android callers' form of [BundledPicture.title] (T64). */
fun BundledPicture.title(strings: Strings): String = title(strings.words)
