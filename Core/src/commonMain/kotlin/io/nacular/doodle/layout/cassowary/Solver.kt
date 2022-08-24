package io.nacular.doodle.layout.cassowary

import io.nacular.doodle.layout.cassowary.Operator.*
import io.nacular.doodle.layout.cassowary.Solver.Type.*
import io.nacular.doodle.layout.cassowary.Strength.Companion.Required

internal class InternalSolverError(val string: String): kotlin.Error()

internal class DuplicateConstraintException(val constraint: Constraint): Exception(constraint.toString())

internal class DuplicateEditVariableException: Exception()

internal class RequiredFailureException: Exception()

internal class UnknownConstraintException(val constraint: Constraint): Exception(constraint.toString())

internal class UnknownEditVariableException: Exception()

internal class UnsatisfiableConstraintException(constraint: Constraint): Exception(constraint.toString())

internal class Solver {
    private class Tag {
        var marker = Symbol()
        var other  = Symbol()
    }

    private class EditInfo(var constraint: Constraint, var tag: Tag, var constant: Double)

    private enum class Type {
        Invalid, External, Slack, Error, Dummy
    }

    private class Symbol constructor(val type: Type = Invalid) {
        override fun toString() = "$type"
    }

    private class Row {
        var constant: Double
        var cells   = mutableMapOf<Symbol, Double>()

        constructor(constant: Double = 0.0) {
            this.constant = constant
        }

        constructor(other: Row) {
            cells.putAll(other.cells)
            constant = other.constant
        }

        /**
         * Add a constant value to the row constant.
         *
         * @return The new value of the constant
         */
        fun add(value: Double) = value.let { constant += it; constant }

        /**
         * Insert a symbol into the row with a given coefficient.
         *
         *
         * If the symbol already exists in the row, the coefficient will be
         * added to the existing coefficient. If the resulting coefficient
         * is zero, the symbol will be removed from the row
         */
        fun insert(symbol: Symbol, coefficient: Double = 1.0) {
            var newCoefficient = coefficient

            cells[symbol]?.let {
                newCoefficient += it
            }

            when {
                nearZero(newCoefficient) -> cells.remove(symbol)
                else                     -> cells[symbol] = newCoefficient
            }
        }

        /**
         * Insert a row into this row with a given coefficient.
         * The constant and the cells of the other row will be multiplied by
         * the coefficient and added to this row. Any cell with a resulting
         * coefficient of zero will be removed from the row.
         *
         * @param other
         */
        fun insert(other: Row, coefficient: Double = 1.0) {
            constant += other.constant * coefficient

            for ((symbol, value) in other.cells) {
                val newCoefficient = value * coefficient

                //changes start here
                cells.getOrPut(symbol) { 0.0 }
                val temp = cells.getOrPut(symbol) { 0.0 } + newCoefficient
                cells[symbol] = temp
                if (nearZero(temp)) {
                    cells.remove(symbol)
                }
            }
        }

        /**
         * Reverse the sign of the constant and all cells in the row.
         */
        fun reverseSign() {
            constant = -constant
            val newCells: MutableMap<Symbol, Double> = LinkedHashMap()
            for (s in cells.keys) {
                val value = -cells[s]!!
                newCells[s] = value
            }
            cells = newCells
        }

        /**
         * Solve the row for the given symbol.
         *
         * This method assumes the row is of the form a * x + b * y + c = 0
         * and (assuming solve for x) will modify the row to represent the
         * right-hand side of x = -b/a * y - c / a. The target symbol will
         * be removed from the row, and the constant and other cells will
         * be multiplied by the negative inverse of the target coefficient.
         * The given symbol *must* exist in the row.
         *
         * @param symbol
         */
        fun solveFor(symbol: Symbol) {
            val coefficient = -1.0 / cells[symbol]!!
            cells.remove(symbol)
            constant *= coefficient
            val newCells = mutableMapOf<Symbol, Double>()
            for (s in cells.keys) {
                val value = cells[s]!! * coefficient
                newCells[s] = value
            }
            cells = newCells
        }

        /**
         * Solve the row for the given symbols.
         *
         * This method assumes the row is of the form x = b * y + c and will
         * solve the row such that y = x / b - c / b. The rhs symbol will be
         * removed from the row, the lhs added, and the result divided by the
         * negative inverse of the rhs coefficient.
         * The lhs symbol *must not* exist in the row, and the rhs symbol
         * must* exist in the row.
         *
         * @param lhs
         * @param rhs
         */
        fun solveFor(lhs: Symbol, rhs: Symbol) {
            insert(lhs, -1.0)
            solveFor(rhs)
        }

        /**
         * Get the coefficient for the given symbol.
         *
         * If the symbol does not exist in the row, zero will be returned.
         *
         * @return
         */
        fun coefficientFor(symbol: Symbol) = when {
            cells.containsKey(symbol) -> cells[symbol]!!
            else                      -> 0.0
        }

        /**
         * Substitute a symbol with the data from another row.
         *
         * Given a row of the form a * x + b and a substitution of the
         * form x = 3 * y + c the row will be updated to reflect the
         * expression 3 * a * y + a * c + b.
         * If the symbol does not exist in the row, this is a no-op.
         */
        fun substitute(symbol: Symbol, row: Row) {
            if (cells.containsKey(symbol)) {
                val coefficient = cells[symbol]!!
                cells.remove(symbol)
                insert(row, coefficient)
            }
        }
    }

    private val constraints    = mutableMapOf<Constraint, Tag>()
    private val rows           = mutableMapOf<Symbol, Row>()
    private val vars           = mutableMapOf<Variable, Symbol>()
    private val edits          = mutableMapOf<Variable, EditInfo>()
    private val infeasibleRows = mutableListOf<Symbol>()
    private val objective      = Row()
    private var artificial     = null as Row?

    internal val variables: Set<Variable> get() = vars.keys
    internal val variableToConstraints = mutableMapOf<Variable, MutableSet<Constraint>>()

    /**
     * Add a constraint to the solver.
     *
     * @param constraint
     * @throws DuplicateConstraintException The given constraint has already been added to the solver.
     * @throws UnsatisfiableConstraintException The given constraint is required and cannot be satisfied.
     */
    @Throws(DuplicateConstraintException::class, UnsatisfiableConstraintException::class)
    fun addConstraint(constraint: Constraint) {
        if (constraints.containsKey(constraint)) {
            throw DuplicateConstraintException(constraint)
        }

        val tag     = Tag()
        val row     = createRow(constraint, tag)
        var subject = chooseSubject(row, tag)

        if (subject.type == Invalid && row.cells.keys.all { it.type == Dummy }) {
            subject = when {
                !nearZero(row.constant) -> throw UnsatisfiableConstraintException(constraint)
                else                    -> tag.marker
            }
        }

        when (subject.type) {
            Invalid -> if (!addWithArtificialVariable(row)) {
                throw UnsatisfiableConstraintException(constraint)
            }
            else    -> {
                row.solveFor(subject)
                substitute(subject, row)
                rows[subject] = row
            }
        }

        constraints[constraint] = tag

        optimize(objective)
    }

    fun remove(variable: Variable) {
        variableToConstraints.remove(variable)?.let {
            it.forEach {
                removeConstraint(it)
            }
        }
    }

    @Throws(UnknownConstraintException::class, InternalSolverError::class)
    fun removeConstraint(constraint: Constraint) {
        val tag = constraints[constraint] ?: throw UnknownConstraintException(constraint)
        constraints.remove(constraint)
        removeConstraintEffects(constraint, tag)

        variableToConstraints.iterator().let {
            while (it.hasNext()) {
                val (variable, constraints) = it.next()
                constraints -= constraint
                if (constraints.isEmpty()) {
                    it.remove()
                    vars.remove(variable)
                }
            }
        }

        var row = rows[tag.marker]

        when {
            row != null -> rows.remove(tag.marker)
            else        -> {
                row = getMarkerLeavingRow(tag.marker) ?: throw InternalSolverError("internal solver error")

                var leaving: Symbol? = null
                for (s in rows.keys) {
                    if (rows[s] == row) {
                        leaving = s
                    }
                }
                if (leaving == null) {
                    throw InternalSolverError("internal solver error")
                }
                rows.remove(leaving)
                row.solveFor(leaving, tag.marker)
                substitute(tag.marker, row)
            }
        }

        optimize(objective)
    }

    @Throws(DuplicateEditVariableException::class, RequiredFailureException::class)
    fun addEditVariable(variable: Variable, strength: Strength) {
        if (edits.containsKey(variable)) {
            throw DuplicateEditVariableException()
        }

        if (strength == Required) {
            throw RequiredFailureException()
        }

        val constraint = Constraint(Expression(Term(variable)), EQ, strength)

        addConstraint(constraint)

        edits[variable] = EditInfo(constraint, constraints[constraint]!!, 0.0)
    }

    @Throws(UnknownEditVariableException::class)
    fun removeEditVariable(variable: Variable) {
        val edit = edits[variable] ?: throw UnknownEditVariableException()

        removeConstraint(edit.constraint)

        edits.remove(variable)
    }

    fun clearEditVariables() {
        edits.values.forEach {
            removeConstraint(it.constraint)
        }
        edits.clear()
    }

    @Throws(UnknownEditVariableException::class)
    fun suggestValue(variable: Variable, value: Double) {
        val info  = edits[variable] ?: throw UnknownEditVariableException()
        val delta = value - info.constant
        info.constant = value
        var row = rows[info.tag.marker]

        if (row != null) {
            if (row.add(-delta) < 0.0) {
                infeasibleRows.add(info.tag.marker)
            }
            dualOptimize()
            return
        }
        row = rows[info.tag.other]

        if (row != null) {
            if (row.add(delta) < 0.0) {
                infeasibleRows.add(info.tag.other)
            }
            dualOptimize()
            return
        }
        for ((symbol, currentRow) in rows) {
            val coefficient = currentRow.coefficientFor(info.tag.marker)
            if (coefficient != 0.0 && currentRow.add(delta * coefficient) < 0.0 && symbol.type != External) {
                infeasibleRows.add(symbol)
            }
        }
        dualOptimize()
    }

    /**
     * Update the values of the external solver variables.
     */
    fun updateVariables() {
        for ((variable, symbol) in vars) {
            when (val row = rows[symbol]) {
                null -> variable(0.0)
                else -> variable(row.constant)
            }
        }
    }

    private fun removeConstraintEffects(constraint: Constraint, tag: Tag) {
        if (tag.marker.type == Error) removeMarkerEffects(tag.marker, constraint.strength)
        if (tag.other.type  == Error) removeMarkerEffects(tag.other,  constraint.strength)
    }

    private fun removeMarkerEffects(marker: Symbol, strength: Strength) {
        val row = rows[marker]

        when {
            row != null -> objective.insert(row,    -strength.value.toDouble())
            else        -> objective.insert(marker, -strength.value.toDouble())
        }
    }

    private fun getMarkerLeavingRow(marker: Symbol): Row? {
        var ratio1 = Double.MAX_VALUE
        var ratio2 = Double.MAX_VALUE
        var first : Row? = null
        var second: Row? = null
        var third : Row? = null

        for ((symbol, row) in rows) {
            val coefficient = row.coefficientFor(marker)

            if (coefficient == 0.0) {
                continue
            }

            when {
                symbol.type == External -> third = row
                coefficient < 0.0       -> {
                    val ratio = -row.constant / coefficient
                    if (ratio < ratio1) {
                        ratio1 = ratio
                        first = row
                    }
                }
                else                    -> {
                    val ratio = row.constant / coefficient
                    if (ratio < ratio2) {
                        ratio2 = ratio
                        second = row
                    }
                }
            }
        }

        return first ?: (second ?: third)
    }

    /**
     * Create a new Row object for the given constraint.
     *
     * The terms in the constraint will be converted to cells in the row.
     * Any term in the constraint with a coefficient of zero is ignored.
     * This method uses the `getVarSymbol` method to get the symbol for
     * the variables added to the row. If the symbol for a given cell
     * variable is basic, the cell variable will be substituted with the
     * basic row.
     *
     * The necessary slack and error variables will be added to the row.
     * If the constant for the row is negative, the sign for the row
     * will be inverted so the constant becomes positive.
     *
     * The tag will be updated with the marker and error symbols to use
     * for tracking the movement of the constraint in the tableau.
     */
    private fun createRow(constraint: Constraint, tag: Tag): Row {
        val expression = constraint.expression
        val row        = Row(expression.constant)

        for (term in expression.terms) {
            if (!nearZero(term.coefficient)) {
                val symbol = getVarSymbol(term.variable)

                val constraints = variableToConstraints.getOrPut(term.variable) {
                    mutableSetOf()
                }

                constraints += constraint

                when (val otherRow = rows[symbol]) {
                    null -> row.insert(symbol,   term.coefficient)
                    else -> row.insert(otherRow, term.coefficient)
                }
            }
        }

        when (constraint.operator) {
            LE, GE -> {
                val coefficient = if (constraint.operator === LE) 1.0 else -1.0
                val slack  = Symbol(Slack)
                tag.marker = slack
                row.insert(slack, coefficient)

                if (constraint.strength < Required) {
                    val error = Symbol(Error)
                    tag.other = error
                    row.insert(error, -coefficient)
                    objective.insert(error, constraint.strength.value.toDouble())
                }
            }
            else   -> {
                when {
                    constraint.strength < Required -> {
                        val errorPlus  = Symbol(Error)
                        val errorMinus = Symbol(Error)
                        tag.marker = errorPlus
                        tag.other  = errorMinus
                        row.insert(errorPlus, -1.0) // v = errorPlus - errorMinus
                        row.insert(errorMinus, 1.0) // v - errorPlus + errorMinus = 0
                        objective.insert(errorPlus,  constraint.strength.value.toDouble())
                        objective.insert(errorMinus, constraint.strength.value.toDouble())
                    }
                    else                           -> {
                        val dummy = Symbol(Dummy)
                        tag.marker = dummy
                        row.insert(dummy)
                    }
                }
            }
        }

        // Ensure the row as a positive constant.
        if (row.constant < 0.0) {
            row.reverseSign()
        }

        return row
    }

    /**
     * Add the row to the tableau using an artificial variable.
     *
     *
     * This will return false if the constraint cannot be satisfied.
     */
    private fun addWithArtificialVariable(row: Row): Boolean {
        //TODO check this

        // Create and add the artificial variable to the tableau
        val art    = Symbol(Slack)
        rows[art]  = Row(row)
        artificial = Row(row)

        // Optimize the artificial objective. This is successful
        // only if the artificial objective is optimized to zero.
        optimize(artificial!!)

        val success = nearZero(artificial!!.constant)

        artificial = null

        // If the artificial variable is basic, pivot the row so that
        // it becomes basic. If the row is constant, exit early.
        val artificialRow = rows[art]
        if (artificialRow != null) {
            /**this looks wrong!!! */
            //rows.remove(rowptr);
            val deleteQueue = rows.filter { (_,row) -> row == artificialRow }.map { it.key }

            deleteQueue.forEach {
                rows.remove(it)
            }

            if (artificialRow.cells.isEmpty()) {
                return success
            }

            val entering = anyPivotableSymbol(artificialRow)

            if (entering.type == Invalid) {
                return false // unsatisfiable (will this ever happen?)
            }

            artificialRow.solveFor(art, entering)
            substitute(entering, artificialRow)
            rows[entering] = artificialRow
        }

        // Remove the artificial variable from the tableau.
        for ((_, r) in rows) {
            r.cells.remove(art)
        }

        objective.cells.remove(art)
        return success
    }

    /**
     * Substitute the parametric symbol with the given row.
     *
     * This method will substitute all instances of the parametric symbol
     * in the tableau and the objective function with the given row.
     */
    private fun substitute(symbol: Symbol, row: Row) {
        for ((key, value) in rows) {
            value.substitute(symbol, row)
            if (key.type != External && value.constant < 0.0) {
                infeasibleRows.add(key)
            }
        }
        objective.substitute(symbol, row)
        artificial?.substitute(symbol, row)
    }

    /**
     * Optimize the system for the given objective function.
     *
     * This method performs iterations of Phase 2 of the simplex method
     * until the objective function reaches a minimum.
     *
     * @throws InternalSolverError The value of the objective function is unbounded.
     */
    private fun optimize(objective: Row) {
        while (true) {
            val entering = getEnteringSymbol(objective)
            if (entering.type == Invalid) {
                return
            }
            val (leaving, row) = getLeavingRow(entering) ?: throw InternalSolverError("The objective is unbounded.")

            rows.remove(leaving)
            row.solveFor(leaving, entering)
            substitute(entering, row)
            rows[entering] = row
        }
    }

    @Throws(InternalSolverError::class)
    private fun dualOptimize() {
        while (infeasibleRows.isNotEmpty()) {
            val leaving = infeasibleRows.removeLast()
            val row     = rows[leaving]
            if (row != null && !nearZero(row.constant) && row.constant < 0.0) {
                val entering = getDualEnteringSymbol(row)
                if (entering.type == Invalid) {
                    throw InternalSolverError("Dual optimize failed")
                }
                rows.remove(leaving)
                row.solveFor(leaving, entering)
                substitute(entering, row)
                rows[entering] = row
            }
        }
    }

    private fun getDualEnteringSymbol(row: Row): Symbol {
        var ratio    = Double.MAX_VALUE
        var entering = Symbol()

        for ((symbol, value) in row.cells) {
            if (symbol.type != Dummy) {
                if (value > 0.0) {
                    val newRatio = objective.coefficientFor(symbol) / value
                    if (newRatio < ratio) {
                        ratio    = newRatio
                        entering = symbol
                    }
                }
            }
        }
        return entering
    }

    /**
     * Get the first Slack or Error symbol in the row.
     *
     *
     * If no such symbol is present, and Invalid symbol will be returned.
     */
    private fun anyPivotableSymbol(row: Row) = row.cells.keys.firstOrNull { it.type == Slack || it.type == Error } ?: Symbol()

    /**
     * Compute the row which holds the exit symbol for a pivot.
     *
     * This documentation is copied from the C++ version and is outdated
     *
     * This method will return an iterator to the row in the row map
     * which holds the exit symbol. If no appropriate exit symbol is
     * found, the end() iterator will be returned. This indicates that
     * the objective function is unbounded.
     */
    private fun getLeavingRow(entering: Symbol): Pair<Symbol, Row>? {
        var ratio = Double.MAX_VALUE
        var result: Pair<Symbol, Row>? = null
        for ((symbol, row) in rows) {
            if (symbol.type != External) {
                val temp = row.coefficientFor(entering)
                if (temp < 0) {
                    val tempRatio = -row.constant / temp
                    if (tempRatio < ratio) {
                        ratio  = tempRatio
                        result = symbol to row
                    }
                }
            }
        }
        return result
    }

    /**
     * Get the symbol for the given variable.
     *
     *
     * If a symbol does not exist for the variable, one will be created.
     */
    private fun getVarSymbol(variable: Variable) = vars.getOrPut(variable) {
        Symbol(External)
    }

    private companion object {
        /**
         * Choose the subject for solving for the row
         *
         *
         * This method will choose the best subject for using as the solve
         * target for the row. An invalid symbol will be returned if there
         * is no valid target.
         * The symbols are chosen according to the following precedence:
         * 1) The first symbol representing an external variable.
         * 2) A negative slack or error tag variable.
         * If a subject cannot be found, an invalid symbol will be returned.
         */
        private fun chooseSubject(row: Row, tag: Tag): Symbol {
            for (key in row.cells.keys) {
                if (key.type == External) {
                    return key
                }
            }

            if ((tag.marker.type == Slack || tag.marker.type == Error) && row.coefficientFor(tag.marker) < 0.0) return tag.marker
            if ((tag.other.type  == Slack || tag.other.type  == Error) && row.coefficientFor(tag.other ) < 0.0) return tag.other

            return Symbol()
        }

        /**
         * Compute the entering variable for a pivot operation.
         *
         * This method will return first symbol in the objective function which
         * is non-dummy and has a coefficient less than zero. If no symbol meets
         * the criteria, it means the objective function is at a minimum, and an
         * invalid symbol is returned.
         */
        private fun getEnteringSymbol(objective: Row) = objective.cells.filter { (key, value) ->
            key.type != Dummy && value < 0.0
        }.firstNotNullOfOrNull {
            it.key
        } ?: Symbol()

        private const val EPS = 1.0e-8

        private fun nearZero(value: Double): Boolean = if (value < 0.0) -value < EPS else value < EPS
    }
}