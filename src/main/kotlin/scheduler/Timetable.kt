package scheduler

import org.optaplanner.core.api.domain.constraintweight.ConstraintConfigurationProvider
import  org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty
import org.optaplanner.core.api.domain.solution.PlanningScore
import org.optaplanner.core.api.domain.solution.PlanningSolution
import org.optaplanner.core.api.domain.solution.drools.ProblemFactCollectionProperty
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore

/** Ovo će biti klasa koja predstavlja problem koji se rešava, kao i rešenje problema
*/

@PlanningSolution
class Timetable (_courseList: List<Course>, _hoursList: List<Hour>){
    val courseList: List<Course> = _courseList
        @ValueRangeProvider(id = "courseRange")
        @ProblemFactCollectionProperty
        get() = field

    val hoursList: List<Hour> = _hoursList
        @PlanningEntityCollectionProperty
        get() = field

    var score: HardSoftScore? = null
        @PlanningScore
        get () = field

    @ConstraintConfigurationProvider
    private lateinit var constraintConfiguration : TimetableConstraintConfiguration
}


