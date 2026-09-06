#!/usr/bin/env python3
"""发布安全审查门禁的 GitHub REST 契约测试。"""

from __future__ import annotations

import importlib.util
import json
import sys
import unittest
from pathlib import Path
from urllib.error import HTTPError, URLError


SCRIPT = Path(__file__).parents[1] / "scripts" / "verify-release-security-review.py"
SPEC = importlib.util.spec_from_file_location("release_security_review", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

TARGET_SHA = "0123456789abcdef0123456789abcdef01234567"
OTHER_SHA = "fedcba9876543210fedcba9876543210fedcba98"


def environment_metadata(**overrides):
    metadata = {
        "id": 600,
        "name": "independent-security-review",
        "updated_at": "2026-09-06T00:00:00Z",
        "can_admins_bypass": False,
        "protection_rules": [
            {
                "type": "required_reviewers",
                "prevent_self_review": True,
                "reviewers": [{"type": "User", "reviewer": {"id": 20, "login": "security-reviewer"}}],
            }
        ],
    }
    metadata.update(overrides)
    return metadata


def workflow_metadata():
    return {"id": 73, "path": ".github/workflows/security-review.yml", "state": "active"}


def workflow_run(**overrides):
    run = {
        "id": 1001,
        "workflow_id": 73,
        "path": ".github/workflows/security-review.yml",
        "head_sha": TARGET_SHA,
        "event": "push",
        "head_branch": "main",
        "status": "completed",
        "conclusion": "success",
        "run_number": 80,
        "run_attempt": 1,
        "created_at": "2026-09-06T00:01:00Z",
        "actor": {"id": 10, "login": "author"},
        "triggering_actor": {"id": 10, "login": "author"},
    }
    run.update(overrides)
    return run


def approval(**overrides):
    record = {
        "state": "approved",
        "environments": [{"id": 600, "name": "independent-security-review"}],
        "user": {"id": 20, "login": "security-reviewer"},
    }
    record.update(overrides)
    return record


class FakeTransport:
    def __init__(self, responses):
        self.responses = list(responses)
        self.requests = []

    def __call__(self, request, timeout):
        self.requests.append(request)
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return json.dumps(response).encode("utf-8")


class ReleaseSecurityReviewTests(unittest.TestCase):
    def test_context_representation_never_contains_token(self):
        self.assertNotIn("test-token", repr(self.context()))

    def test_transport_refuses_redirects(self):
        handler = MODULE.RejectRedirects()
        self.assertIsNone(handler.redirect_request(None, None, 302, "Found", {}, "https://example.test"))

    def test_rejects_inactive_workflow(self):
        responses = self.responses()
        responses[1]["state"] = "disabled_manually"
        self.assert_rejected(responses, "尚未启用")

    def test_rejects_wrong_run_path(self):
        self.assert_rejected(self.responses(run=workflow_run(path=".github/workflows/other.yml")), "路径不匹配")

    def context(self, **overrides):
        values = {
            "repository": "example/ai-plus",
            "token": "test-token",
            "github_actions": "true",
            "server_url": "https://github.com",
            "sha": TARGET_SHA,
            "ref": "refs/heads/main",
            "event_name": "workflow_dispatch",
        }
        values.update(overrides)
        return MODULE.GateContext(**values)

    def responses(self, run=None, approvals=None, environment=None, pages=None):
        if pages is None:
            pages = [{"total_count": 1, "workflow_runs": [workflow_run() if run is None else run]}]
        return [
            environment_metadata() if environment is None else environment,
            workflow_metadata(),
            *pages,
            [approval()] if approvals is None else approvals,
        ]

    def verify(self, responses, target_sha=TARGET_SHA, context=None):
        transport = FakeTransport(responses)
        result = MODULE.verify_release_security_review(target_sha, self.context() if context is None else context, transport)
        return result, transport

    def assert_rejected(self, responses, fragment):
        with self.assertRaisesRegex(MODULE.GateError, fragment):
            self.verify(responses)

    def test_accepts_exact_first_attempt_with_independent_environment_approval(self):
        result, transport = self.verify(self.responses())
        self.assertIn("run_id=1001", result)
        self.assertIn("reviewer=security-reviewer", result)
        self.assertEqual(4, len(transport.requests))
        self.assertTrue(all(request.full_url.startswith("https://api.github.com/") for request in transport.requests))
        self.assertEqual("Bearer test-token", transport.requests[0].get_header("Authorization"))
        self.assertNotIn("status=", transport.requests[2].full_url)

    def test_rejects_missing_environment_protection(self):
        self.assert_rejected(self.responses(environment=environment_metadata(protection_rules=[])), "required_reviewers")

    def test_rejects_missing_admin_bypass_evidence(self):
        self.assert_rejected(self.responses(environment=environment_metadata(can_admins_bypass=True)), "管理员绕过")

    def test_rejects_missing_approval(self):
        self.assert_rejected(self.responses(approvals=[]), "没有来自当前独立审查者")

    def test_rejects_approval_for_a_different_environment(self):
        self.assert_rejected(
            self.responses(approvals=[approval(environments=[{"name": "package-release"}])]),
            "没有来自当前独立审查者",
        )

    def test_rejects_self_approval_by_run_actor(self):
        self_review_environment = environment_metadata(
            protection_rules=[
                {
                    "type": "required_reviewers",
                    "prevent_self_review": True,
                    "reviewers": [{"type": "User", "reviewer": {"id": 10, "login": "author"}}],
                }
            ]
        )
        self.assert_rejected(
            self.responses(environment=self_review_environment, approvals=[approval(user={"id": 10, "login": "author-renamed"})]),
            "没有来自当前独立审查者",
        )

    def test_rejects_self_approval_by_rerun_triggering_actor(self):
        run = workflow_run(triggering_actor={"id": 11, "login": "rerunner"})
        self_review_environment = environment_metadata(
            protection_rules=[
                {
                    "type": "required_reviewers",
                    "prevent_self_review": True,
                    "reviewers": [{"type": "User", "reviewer": {"id": 11, "login": "rerunner"}}],
                }
            ]
        )
        self.assert_rejected(
            self.responses(
                run=run,
                environment=self_review_environment,
                approvals=[approval(user={"id": 11, "login": "RERUNNER"})],
            ),
            "没有来自当前独立审查者",
        )

    def test_rejects_other_commit(self):
        self.assert_rejected(self.responses(run=workflow_run(head_sha="f" * 40)), "没有找到")

    def test_rejects_other_workflow(self):
        self.assert_rejected(self.responses(run=workflow_run(workflow_id=99)), "没有找到")

    def test_rejects_other_event(self):
        self.assert_rejected(self.responses(run=workflow_run(event="workflow_dispatch")), "没有找到")

    def test_rejects_other_branch(self):
        self.assert_rejected(self.responses(run=workflow_run(head_branch="release")), "没有找到")

    def test_rejects_incomplete_sha_before_making_an_api_call(self):
        with self.assertRaisesRegex(MODULE.GateError, "40 位提交 SHA"):
            MODULE.verify_release_security_review("abc", self.context(), FakeTransport([]))

    def test_rejects_sha_that_differs_from_current_actions_checkout(self):
        with self.assertRaisesRegex(MODULE.GateError, "GITHUB_SHA"):
            self.verify([], OTHER_SHA)

    def test_rejects_non_dispatch_or_non_main_release_context(self):
        with self.assertRaisesRegex(MODULE.GateError, "workflow_dispatch"):
            self.verify([], context=self.context(event_name="push"))
        with self.assertRaisesRegex(MODULE.GateError, "workflow_dispatch"):
            self.verify([], context=self.context(ref="refs/heads/release"))

    def test_rejects_network_failure_without_token_echo(self):
        with self.assertRaisesRegex(MODULE.GateError, "查询失败或超时") as error:
            self.verify([URLError("offline")])
        self.assertNotIn("test-token", str(error.exception))

    def test_rejects_insufficient_api_permission(self):
        forbidden = HTTPError("https://api.github.com/", 403, "forbidden", {}, None)
        self.assert_rejected([forbidden], "没有读取 Actions/Environment")

    def test_rejects_non_github_actions_context(self):
        with self.assertRaisesRegex(MODULE.GateError, "GitHub Actions"):
            self.verify([], context=self.context(github_actions="false"))

    def test_reads_second_page_before_accepting_a_matching_run(self):
        first_page = {"total_count": 101, "workflow_runs": [workflow_run(id=index, head_sha="f" * 40) for index in range(100)]}
        second_page = {"total_count": 101, "workflow_runs": [workflow_run()]}
        result, transport = self.verify(self.responses(pages=[first_page, second_page]))
        self.assertIn("run_id=1001", result)
        self.assertEqual(5, len(transport.requests))
        self.assertIn("page=2", transport.requests[3].full_url)

    def test_rejects_rerun_because_approval_history_has_no_attempt_binding(self):
        self.assert_rejected(self.responses(run=workflow_run(run_attempt=2)), "审批历史不含 attempt 关联")

    def test_uses_user_ids_when_login_case_or_name_changes(self):
        changed_login_environment = environment_metadata(
            protection_rules=[
                {
                    "type": "required_reviewers",
                    "prevent_self_review": True,
                    "reviewers": [{"type": "User", "reviewer": {"id": 20, "login": "Security-Reviewer"}}],
                }
            ]
        )
        result, _ = self.verify(
            self.responses(environment=changed_login_environment, approvals=[approval(user={"id": 20, "login": "security-reviewer"})])
        )
        self.assertIn("reviewer=security-reviewer", result)

    def test_rejects_approval_for_recreated_environment_with_same_name(self):
        self.assert_rejected(
            self.responses(approvals=[approval(environments=[{"id": 599, "name": "independent-security-review"}])]),
            "没有来自当前独立审查者",
        )

    def test_rejects_approval_from_user_not_in_current_required_reviewers(self):
        self.assert_rejected(
            self.responses(approvals=[approval(user={"id": 99, "login": "unlisted-reviewer"})]),
            "没有来自当前独立审查者",
        )

    def test_rejects_team_required_reviewers_without_membership_evidence(self):
        team_environment = environment_metadata(
            protection_rules=[
                {
                    "type": "required_reviewers",
                    "prevent_self_review": True,
                    "reviewers": [{"type": "Team", "reviewer": {"id": 88, "slug": "security"}}],
                }
            ]
        )
        self.assert_rejected(self.responses(environment=team_environment), "团队审批成员")

    def test_rejects_environment_configuration_changed_after_run_started(self):
        changed_environment = environment_metadata(updated_at="2026-09-06T00:02:00Z")
        self.assert_rejected(self.responses(environment=changed_environment), "运行创建后变更")

    def test_rejects_latest_failed_run_instead_of_accepting_old_success(self):
        old_success = workflow_run(id=1001, run_number=80)
        latest_failure = workflow_run(id=1002, run_number=81, conclusion="failure")
        page = {"total_count": 2, "workflow_runs": [old_success, latest_failure]}
        self.assert_rejected(self.responses(pages=[page]), "最新独立安全审查运行尚未成功")

    def test_rejects_latest_pending_run_instead_of_accepting_old_success(self):
        old_success = workflow_run(id=1001, run_number=80)
        latest_pending = workflow_run(id=1002, run_number=81, status="in_progress", conclusion=None)
        page = {"total_count": 2, "workflow_runs": [old_success, latest_pending]}
        self.assert_rejected(self.responses(pages=[page]), "最新独立安全审查运行尚未成功")

    def test_rejects_latest_rerun_instead_of_accepting_old_success(self):
        old_success = workflow_run(id=1001, run_number=80)
        latest_rerun = workflow_run(id=1002, run_number=81, run_attempt=2)
        page = {"total_count": 2, "workflow_runs": [old_success, latest_rerun]}
        self.assert_rejected(self.responses(pages=[page]), "审批历史不含 attempt 关联")


if __name__ == "__main__":
    unittest.main()
