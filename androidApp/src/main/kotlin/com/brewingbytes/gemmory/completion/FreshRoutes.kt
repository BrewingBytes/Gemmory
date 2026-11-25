package com.brewingbytes.gemmory.completion

import com.brewingbytes.gemmory.nav.Route

/** [route] as the player sees it: fresh if the stack says so or its canvas was just finished. */
fun FreshFinishTracker.isFresh(route: Route.Completion): Boolean = isFresh(route.ref, route.fresh)

/** The player left [route]: true if it was fresh, and it is a still from now on. */
fun FreshFinishTracker.left(route: Route.Completion): Boolean = left(route.ref, route.fresh)
