package com.pmfby.evidence.core.rbac

/**
 * Client-side mirror of the backend's RBAC scoping (backend/src/middleware/rbac.js,
 * backend/src/routes/farmers.js) — used to filter the Room-cached farmer/case list for
 * display and for deciding which actions the UI even offers. This is NEVER the source of
 * truth: the server re-checks every request independently (never trust the client), but
 * without this the offline-cached list would show stale/wrong rows after a reassignment
 * while the device is offline, or would show action buttons the server will reject.
 */
enum class RoleKey {
    SUPER_ADMIN,
    MANAGER,
    SURVEYOR,
}

data class SessionUser(
    val id: String,
    val roleKey: RoleKey,
    val scopeStateId: String?,
    val scopeDistrictId: String?,
)

data class CachedFarmer(
    val id: String,
    val stateId: String?,
    val districtId: String?,
    val assignedUserId: String?,
)

object RbacFilter {

    /**
     * Mirrors the farmers-list route's SQL WHERE clause: surveyor sees only farmers
     * actively assigned to them; manager sees only their geographic scope (and a manager
     * with no scope configured fails closed — sees nothing, never org-wide); super_admin
     * sees everything.
     */
    fun visibleFarmers(user: SessionUser, allCached: List<CachedFarmer>): List<CachedFarmer> {
        return when (user.roleKey) {
            RoleKey.SUPER_ADMIN -> allCached
            RoleKey.SURVEYOR -> allCached.filter { it.assignedUserId == user.id }
            RoleKey.MANAGER -> {
                val districtScope = user.scopeDistrictId
                val stateScope = user.scopeStateId
                when {
                    districtScope != null -> allCached.filter { it.districtId == districtScope }
                    stateScope != null -> allCached.filter { it.stateId == stateScope }
                    else -> emptyList() // fail closed, matches managerScopeFilter in rbac.js
                }
            }
        }
    }

    /** Only surveyors initiate calls/SMS/evidence/emergency actions — mirrors every route's check. */
    fun canInitiateFieldActions(user: SessionUser): Boolean = user.roleKey == RoleKey.SURVEYOR

    /** Only super_admin may view the Integrity Concerns list (anyone may submit one). */
    fun canViewIntegrityConcerns(user: SessionUser): Boolean = user.roleKey == RoleKey.SUPER_ADMIN

    /** Super admin and manager (in scope) may review discrepancy flags / emergency incidents. */
    fun canReviewFlags(user: SessionUser): Boolean =
        user.roleKey == RoleKey.SUPER_ADMIN || user.roleKey == RoleKey.MANAGER

    /** A surveyor may raise a flag but never review/resolve their own — mirrors cases.js intent. */
    fun canResolveOwnRaisedFlag(user: SessionUser): Boolean = false
}
