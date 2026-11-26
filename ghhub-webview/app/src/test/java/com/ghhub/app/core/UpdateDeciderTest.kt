package com.ghhub.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 更新状态机单元测试：验证方案 6.2 的三条硬约束。
 * 与 Web 原型的 logic_test.js 断言一一对应。
 */
class UpdateDeciderTest {

    private fun local(ver: String, fp: String) = UpdateDecider.LocalInfo(ver, fp)
    private fun remote(ver: String, fp: String, log: List<String> = emptyList()) =
        UpdateDecider.RemoteInfo(ver, fp, log)

    @Test
    fun `同版本指纹一致_to_NoUpdate`() {
        val s = UpdateDecider.decide(local("1.2.20", "9f3ac21b"), remote("1.2.20", "9f3ac21b"), emptySet(), false)
        assertEquals("NoUpdate", s.name)
    }

    @Test
    fun `补丁更新指纹一致_to_OptionalUpdate`() {
        val s = UpdateDecider.decide(local("1.2.20", "9f3ac21b"), remote("1.2.21", "9f3ac21b"), emptySet(), false)
        assertEquals("OptionalUpdate", s.name)
    }

    @Test
    fun `次版本变化_to_ForcedUpdate`() {
        val s = UpdateDecider.decide(local("1.4.2", "b71d0e4a"), remote("2.0.0", "c882ff10"), emptySet(), true)
        assertEquals("ForcedUpdate", s.name)
    }

    @Test
    fun `同版本指纹不一致_to_ForcedUpdate_即使版本号相同`() {
        val s = UpdateDecider.decide(local("1.2.20", "9f3ac21b"), remote("1.2.20", "deadbeef"), emptySet(), false)
        assertEquals("ForcedUpdate", s.name)
    }

    @Test
    fun `版本解析失败_to_Unknown_静默绝不兜底_0_0_0`() {
        val s = UpdateDecider.decide(local("3.2.0", "c31fa802"), remote("latest", "c31fa802"), emptySet(), false)
        assertEquals("Unknown", s.name)
    }

    @Test
    fun `远端更旧_to_NoUpdate`() {
        val s = UpdateDecider.decide(local("1.2.20", "9f3ac21b"), remote("1.1.9", "9f3ac21b"), emptySet(), false)
        assertEquals("NoUpdate", s.name)
    }

    @Test
    fun `skip双键命中_to_NoUpdate_修复_githup_No40`() {
        val skipped = setOf(SkipKey("1.2.21", "9f3ac21b"))
        val s = UpdateDecider.decide(local("1.2.20", "9f3ac21b"), remote("1.2.21", "9f3ac21b"), skipped, false)
        assertEquals("NoUpdate", s.name)
    }

    @Test
    fun `skip后指纹变化_to_ForcedUpdate_忽略skip`() {
        val skipped = setOf(SkipKey("1.2.21", "9f3ac21b"))
        val s = UpdateDecider.decide(local("1.2.20", "9f3ac21b"), remote("1.2.21", "newfp00"), skipped, false)
        assertEquals("ForcedUpdate", s.name)
    }

    @Test
    fun `语义版本解析`() {
        assertEquals(1, SemVer.parse("v1.2.20")?.major)
        assertEquals(20, SemVer.parse("1.2.20")?.patch)
        assertEquals(null, SemVer.parse("latest"))
    }

    @Test
    fun `主次版本变化判定`() {
        assertEquals(true, UpdateDecider.isMajorOrMinorBump("1.4.2", "2.0.0"))
        assertEquals(true, UpdateDecider.isMajorOrMinorBump("1.2.20", "1.3.0"))
        assertEquals(false, UpdateDecider.isMajorOrMinorBump("1.2.20", "1.2.21"))
    }
}
