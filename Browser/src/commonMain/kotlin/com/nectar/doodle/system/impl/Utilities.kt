package com.nectar.doodle.system.impl

import com.nectar.doodle.HTMLButtonElement
import com.nectar.doodle.HTMLElement
import com.nectar.doodle.HTMLInputElement
import com.nectar.doodle.dom.EventTarget
import com.nectar.doodle.dom.hasScrollOverflow


internal fun isNativeElement  (target: EventTarget?) = target is HTMLElement && (target.hasScrollOverflow || target is HTMLButtonElement || target is HTMLInputElement)
internal fun nativeScrollPanel(target: EventTarget?) = target is HTMLElement &&  target.hasScrollOverflow