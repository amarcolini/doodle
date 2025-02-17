package io.nacular.doodle.layout

import io.nacular.doodle.core.Container
import io.nacular.doodle.core.container
import io.nacular.doodle.core.view
import io.nacular.doodle.geometry.Rectangle
import io.nacular.doodle.geometry.Size
import io.nacular.doodle.utils.HorizontalAlignment.Center
import io.nacular.doodle.utils.HorizontalAlignment.Right
import io.nacular.doodle.utils.VerticalAlignment.Bottom
import io.nacular.doodle.utils.VerticalAlignment.Middle
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.expect

class HorizontalFlowLayoutTests {
    @Test @JsName("leftNoWrapNoSpacing") fun `left, no wrap, no spacing`() {
        validate { container ->
            var x = container.insets.left
            val y = container.insets.top

            Info(bounds = container.map { child ->
                child.bounds.at(x, y).also {
                    x += child.width
                }
            })
        }
    }

    @Test @JsName("leftNoWrapNoSpacingMiddle") fun `left, no wrap, no spacing, middle`() {
        validate { container ->
            var x      = 0.0
            val middle = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height / 2 }

            Info(HorizontalFlowLayout(spacing = 0.0, verticalAlignment = Middle), container.map { child ->
                child.bounds.at(
                    x = container.insets.left + x,
                    y = middle - child.height / 2
                ).also {
                    x += child.width
                }
            })
        }
    }

    @Test @JsName("leftNoWrapNoSpacingBottom") fun `left, no wrap, no spacing, bottom`() {
        validate { container ->
            var x      = 0.0
            val bottom = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height }

            Info(HorizontalFlowLayout(spacing = 0.0, verticalAlignment = Bottom), container.map { child ->
                child.bounds.at(
                    x = container.insets.left + x,
                    y = bottom - child.height
                ).also {
                    x += child.width
                }
            })
        }
    }

    @Test @JsName("leftNoWrap") fun `left, no wrap`() {
        validate { container ->
            var x       = container.insets.left
            val y       = container.insets.top
            val spacing = 10.0

            Info(HorizontalFlowLayout(spacing = spacing), container.map { child ->
                child.bounds.at(x, y).also {
                    x += child.width + spacing
                }
            })
        }
    }

    @Test @JsName("leftNoWrapMiddle") fun `left, no wrap, middle`() {
        validate { container ->
            var x       = 0.0
            val spacing = 10.0
            val middle  = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height / 2 }

            Info(HorizontalFlowLayout(spacing = spacing, verticalAlignment = Middle), container.map { child ->
                child.bounds.at(
                    x = container.insets.left + x,
                    y = middle - child.height / 2
                ).also {
                    x += child.width + spacing
                }
            })
        }
    }

    @Test @JsName("leftNoWrapBottom") fun `left, no wrap, bottom`() {
        validate { container ->
            var x       = 0.0
            val spacing = 10.0
            val bottom  = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height }

            Info(HorizontalFlowLayout(spacing = spacing, verticalAlignment = Bottom), container.map { child ->
                child.bounds.at(
                    x = container.insets.left + x,
                    y = bottom - child.height
                ).also {
                    x += child.width + spacing
                }
            })
        }
    }

    @Test @JsName("centerNoWrapNoSpacing") fun `center, no wrap, no spacing`() {
        validate { container ->
            var x = (container.width - container.sumOf { it.width }) / 2
            val y = container.insets.top

            Info(HorizontalFlowLayout(spacing = 0.0, justification = Center), bounds = container.map { child ->
                child.bounds.at(x, y).also {
                    x += child.width
                }
            })
        }
    }

    @Test @JsName("centerNoWrapNoSpacingMiddle") fun `center, no wrap, no spacing, middle`() {
        validate { container ->
            var x      = (container.width - container.sumOf { it.width }) / 2
            val middle = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height / 2 }

            Info(HorizontalFlowLayout(spacing = 0.0, verticalAlignment = Middle, justification = Center), container.map { child ->
                child.bounds.at(x, middle - child.height / 2).also {
                    x += child.width
                }
            })
        }
    }

    @Test @JsName("centerNoWrapNoSpacingBottom") fun `center, no wrap, no spacing, bottom`() {
        validate { container ->
            var x      = (container.width - container.sumOf { it.width }) / 2
            val bottom = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height }

            Info(HorizontalFlowLayout(spacing = 0.0, verticalAlignment = Bottom, justification = Center), container.map { child ->
                child.bounds.at(x, bottom - child.height).also {
                    x += child.width
                }
            })
        }
    }

    @Test @JsName("centerNoWrap") fun `center, no wrap`() {
        validate { container ->
            val spacing = 10.0
            var x       = (container.width - (container.sumOf { it.width } + (container.children.size - 1) * spacing)) / 2
            val y       = container.insets.top

            Info(HorizontalFlowLayout(spacing = spacing, justification = Center), container.map { child ->
                child.bounds.at(x, y).also {
                    x += child.width + spacing
                }
            })
        }
    }

    @Test @JsName("centerNoWrapMiddle") fun `center, no wrap, middle`() {
        validate { container ->
            val spacing = 10.0
            var x       = (container.width - (container.sumOf { it.width } + (container.children.size - 1) * spacing)) / 2
            val middle  = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height / 2 }

            Info(HorizontalFlowLayout(spacing = spacing, verticalAlignment = Middle, justification = Center), container.map { child ->
                child.bounds.at(x, middle - child.height / 2).also {
                    x += child.width + spacing
                }
            })
        }
    }

    @Test @JsName("centerNoWrapBottom") fun `center, no wrap, bottom`() {
        validate { container ->
            val spacing = 10.0
            var x       = (container.width - (container.sumOf { it.width } + (container.children.size - 1) * spacing)) / 2
            val bottom  = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height }

            Info(HorizontalFlowLayout(spacing = spacing, verticalAlignment = Bottom, justification = Center), container.map { child ->
                child.bounds.at(x, bottom - child.height).also {
                    x += child.width + spacing
                }
            })
        }
    }

    @Test @JsName("rightNoWrapNoSpacing") fun `right, no wrap, no spacing`() {
        validate { container ->
            var x = container.width - container.insets.right - container.sumOf { it.width }
            val y = container.insets.top

            Info(HorizontalFlowLayout(spacing = 0.0, justification = Right), bounds = container.map { child ->
                child.bounds.at(x, y).also {
                    x += child.width
                }
            })
        }
    }

    @Test @JsName("rightNoWrapNoSpacingMiddle") fun `right, no wrap, no spacing, middle`() {
        validate { container ->
            var x      = container.width - container.insets.right - container.sumOf { it.width }
            val middle = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height / 2 }

            Info(HorizontalFlowLayout(spacing = 0.0, verticalAlignment = Middle, justification = Right), container.map { child ->
                child.bounds.at(x, middle - child.height / 2).also {
                    x += child.width
                }
            })
        }
    }

    @Test @JsName("rightNoWrapNoSpacingBottom") fun `right, no wrap, no spacing, bottom`() {
        validate { container ->
            var x      = container.width - container.insets.right - container.sumOf { it.width }
            val bottom = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height }

            Info(HorizontalFlowLayout(spacing = 0.0, verticalAlignment = Bottom, justification = Right), container.map { child ->
                child.bounds.at(x, bottom - child.height).also {
                    x += child.width
                }
            })
        }
    }

    @Test @JsName("rightNoWrap") fun `right, no wrap`() {
        validate { container ->
            val spacing = 10.0
            var x       = container.width - container.insets.right - (container.sumOf { it.width } + (container.children.size - 1) * spacing)
            val y       = container.insets.top

            Info(HorizontalFlowLayout(spacing = spacing, justification = Right), container.map { child ->
                child.bounds.at(x, y).also {
                    x += child.width + spacing
                }
            })
        }
    }

    @Test @JsName("rightNoWrapMiddle") fun `right, no wrap, middle`() {
        validate { container ->
            val spacing = 10.0
            var x       = container.width - container.insets.right - (container.sumOf { it.width } + (container.children.size - 1) * spacing)
            val middle  = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height / 2 }

            Info(HorizontalFlowLayout(spacing = spacing, verticalAlignment = Middle, justification = Right), container.map { child ->
                child.bounds.at(x, middle - child.height / 2).also {
                    x += child.width + spacing
                }
            })
        }
    }

    @Test @JsName("rightNoWrapBottom") fun `right, no wrap, bottom`() {
        validate { container ->
            val spacing = 10.0
            var x       = container.width - container.insets.right - (container.sumOf { it.width } + (container.children.size - 1) * spacing)
            val bottom  = container.insets.top + container.maxWith { a, b -> a.width.compareTo(b.width) }.run { y + height }

            Info(HorizontalFlowLayout(spacing = spacing, verticalAlignment = Bottom, justification = Right), container.map { child ->
                child.bounds.at(x, bottom - child.height).also {
                    x += child.width + spacing
                }
            })
        }
    }

    private data class Info(val layout: HorizontalFlowLayout = HorizontalFlowLayout(spacing = 0.0), val bounds: List<Rectangle>)

    private fun validate(config: (Container) -> Info) {
        val container = container {
            size      = Size(500)
            insets    = Insets(11.0, 12.0, 3.0, 5.0)
            children += (0 until 4).mapIndexed { index, _ ->
                view { size = Size(100 + (5 * index), 100 + (3 * index)) }
            }
            layout = HorizontalFlowLayout(spacing = 0.0)
        }

        val info = config(container)

        container.layout = info.layout

        container.doLayout_()

        expect(info.bounds) { container.map { it.bounds } }
    }
}