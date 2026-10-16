package com.pmfby.evidence.core.rbac

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RbacFilterTest {

    private val farmers = listOf(
        CachedFarmer(id = "f1", stateId = "HR", districtId = "ROHTAK", assignedUserId = "surveyor-1"),
        CachedFarmer(id = "f2", stateId = "HR", districtId = "ROHTAK", assignedUserId = "surveyor-2"),
        CachedFarmer(id = "f3", stateId = "HR", districtId = "HISAR", assignedUserId = "surveyor-1"),
        CachedFarmer(id = "f4", stateId = "PB", districtId = "LUDHIANA", assignedUserId = null),
    )

    @Test
    fun `surveyor sees only farmers assigned to them`() {
        val surveyor = SessionUser("surveyor-1", RoleKey.SURVEYOR, null, null)
        val visible = RbacFilter.visibleFarmers(surveyor, farmers)
        assertEquals(setOf("f1", "f3"), visible.map { it.id }.toSet())
    }

    @Test
    fun `manager scoped to a district sees only that district`() {
        val manager = SessionUser("mgr-1", RoleKey.MANAGER, scopeStateId = "HR", scopeDistrictId = "ROHTAK")
        val visible = RbacFilter.visibleFarmers(manager, farmers)
        assertEquals(setOf("f1", "f2"), visible.map { it.id }.toSet())
    }

    @Test
    fun `manager scoped only to a state sees the whole state`() {
        val manager = SessionUser("mgr-2", RoleKey.MANAGER, scopeStateId = "HR", scopeDistrictId = null)
        val visible = RbacFilter.visibleFarmers(manager, farmers)
        assertEquals(setOf("f1", "f2", "f3"), visible.map { it.id }.toSet())
    }

    @Test
    fun `manager with no scope configured fails closed and sees nothing`() {
        val manager = SessionUser("mgr-3", RoleKey.MANAGER, scopeStateId = null, scopeDistrictId = null)
        val visible = RbacFilter.visibleFarmers(manager, farmers)
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `super admin sees everything regardless of scope`() {
        val admin = SessionUser("admin-1", RoleKey.SUPER_ADMIN, null, null)
        val visible = RbacFilter.visibleFarmers(admin, farmers)
        assertEquals(4, visible.size)
    }

    @Test
    fun `only surveyors can initiate field actions`() {
        assertTrue(RbacFilter.canInitiateFieldActions(SessionUser("s", RoleKey.SURVEYOR, null, null)))
        assertFalse(RbacFilter.canInitiateFieldActions(SessionUser("m", RoleKey.MANAGER, null, null)))
        assertFalse(RbacFilter.canInitiateFieldActions(SessionUser("a", RoleKey.SUPER_ADMIN, null, null)))
    }

    @Test
    fun `only super admin can view integrity concerns list`() {
        assertTrue(RbacFilter.canViewIntegrityConcerns(SessionUser("a", RoleKey.SUPER_ADMIN, null, null)))
        assertFalse(RbacFilter.canViewIntegrityConcerns(SessionUser("m", RoleKey.MANAGER, null, null)))
        assertFalse(RbacFilter.canViewIntegrityConcerns(SessionUser("s", RoleKey.SURVEYOR, null, null)))
    }

    @Test
    fun `a surveyor can never resolve their own raised flag`() {
        assertFalse(RbacFilter.canResolveOwnRaisedFlag(SessionUser("s", RoleKey.SURVEYOR, null, null)))
    }
}
