package com.example.lifeos.domain.usecases

import com.example.lifeos.data.database.dao.MilestoneDao
import com.example.lifeos.domain.repositories.TaskRepository
import javax.inject.Inject

/**
 * Computes real progress for a Goal or Project (prompt sections 15-16: both
 * entities have a "Progress" field). Previously [com.example.lifeos.data.database.entities.GoalEntity.progressPercentage]
 * and [com.example.lifeos.data.database.entities.ProjectEntity.progressPercentage]
 * were plain stored columns that nothing ever updated, so they silently sat
 * at 0 while the UI displayed them as if they meant something — exactly the
 * kind of "fake functionality" prompt section 62 rules out.
 *
 * Progress is milestone-based when milestones exist (a milestone is an
 * explicit unit of "done" the user defined), and falls back to the
 * completed/total ratio of linked tasks otherwise. A goal/project with
 * neither milestones nor linked tasks yet has no basis for a percentage, so
 * it reports 0 rather than a fabricated number.
 */
class GoalProjectProgressUseCase @Inject constructor(
    private val milestoneDao: MilestoneDao,
    private val taskRepository: TaskRepository
) {
    suspend fun computeGoalProgress(goalId: String): Int {
        val totalMilestones = milestoneDao.countMilestonesForGoal(goalId)
        if (totalMilestones > 0) {
            val completed = milestoneDao.countCompletedMilestonesForGoal(goalId)
            return (completed * 100) / totalMilestones
        }
        val totalTasks = taskRepository.countTasksForGoal(goalId)
        if (totalTasks == 0) return 0
        val completedTasks = taskRepository.countCompletedTasksForGoal(goalId)
        return (completedTasks * 100) / totalTasks
    }

    suspend fun computeProjectProgress(projectId: String): Int {
        val totalMilestones = milestoneDao.countMilestonesForProject(projectId)
        if (totalMilestones > 0) {
            val completed = milestoneDao.countCompletedMilestonesForProject(projectId)
            return (completed * 100) / totalMilestones
        }
        val totalTasks = taskRepository.countTasksForProject(projectId)
        if (totalTasks == 0) return 0
        val completedTasks = taskRepository.countCompletedTasksForProject(projectId)
        return (completedTasks * 100) / totalTasks
    }
}
