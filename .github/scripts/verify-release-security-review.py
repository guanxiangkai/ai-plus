#!/usr/bin/env python3
"""验证发布提交具有可归属的独立 GitHub Environment 人工安全审查。

GitHub API 身份与审批记录不能证明工作流实际执行了何种扫描。启用此门禁前，
仍须保护安全审查工作流及其依赖、要求独立代码审查，并确保发布工作流调用本文件。
本草稿本身尚未接入发布工作流。
"""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable, Mapping
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener


API_BASE = "https://api.github.com"
API_VERSION = "2022-11-28"
SECURITY_REVIEW_WORKFLOW = "security-review.yml"
SECURITY_REVIEW_WORKFLOW_PATH = ".github/workflows/security-review.yml"
SECURITY_REVIEW_ENVIRONMENT = "independent-security-review"
EXPECTED_EVENT = "push"
EXPECTED_BRANCH = "main"
MAX_RUN_PAGES = 10
PER_PAGE = 100
SHA_PATTERN = re.compile(r"^[0-9a-fA-F]{40}$")
REPOSITORY_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*$")


class GateError(RuntimeError):
    """门禁不能证明审批契约时抛出的非秘密错误。"""


Transport = Callable[[Request, float], bytes]


@dataclass(frozen=True)
class GateContext:
    """执行门禁所需的受信任 Actions 上下文。"""

    repository: str
    token: str = field(repr=False)
    github_actions: str
    server_url: str
    sha: str
    ref: str
    event_name: str

    @classmethod
    def from_environment(cls, environment: Mapping[str, str]) -> "GateContext":
        return cls(
            repository=environment.get("GITHUB_REPOSITORY", ""),
            token=environment.get("GITHUB_TOKEN", ""),
            github_actions=environment.get("GITHUB_ACTIONS", ""),
            server_url=environment.get("GITHUB_SERVER_URL", ""),
            sha=environment.get("GITHUB_SHA", ""),
            ref=environment.get("GITHUB_REF", ""),
            event_name=environment.get("GITHUB_EVENT_NAME", ""),
        )

    def validate(self, target_sha: str) -> tuple[str, str]:
        if self.github_actions != "true":
            raise GateError("拒绝发布：门禁只能在 GitHub Actions 受信任运行环境中执行。")
        if self.server_url != "https://github.com":
            raise GateError("拒绝发布：门禁只接受 github.com 的 GitHub Actions 运行环境。")
        if self.event_name != "workflow_dispatch" or self.ref != "refs/heads/main":
            raise GateError("拒绝发布：门禁只接受 main 分支的 workflow_dispatch 发布上下文。")
        if not REPOSITORY_PATTERN.fullmatch(self.repository):
            raise GateError("拒绝发布：GITHUB_REPOSITORY 不是受信任的 owner/repository 标识。")
        if not self.token:
            raise GateError("拒绝发布：缺少 GITHUB_TOKEN，无法查询不可伪造的审查记录。")
        if not SHA_PATTERN.fullmatch(self.sha) or self.sha.lower() != target_sha:
            raise GateError("拒绝发布：目标 SHA 必须等于当前 GitHub Actions 执行上下文的 GITHUB_SHA。")
        owner, repository = self.repository.split("/", 1)
        return owner, repository


class RejectRedirects(HTTPRedirectHandler):
    """拒绝 API 重定向，避免身份请求头随跳转流向其他地址。"""

    def redirect_request(self, request, response, code, message, headers, new_url):
        return None


def default_transport(request: Request, timeout: float) -> bytes:
    """通过固定 GitHub REST API 域名发起有超时的 GET 请求。"""

    with build_opener(RejectRedirects()).open(request, timeout=timeout) as response:
        return response.read()


class GitHubApi:
    """只读 GitHub REST 客户端；令牌仅在进程内请求头中使用。"""

    def __init__(self, repository: str, token: str, transport: Transport = default_transport) -> None:
        self._repository = repository
        self._token = token
        self._transport = transport

    def get_json(self, path: str, query: Mapping[str, Any] | None = None) -> Any:
        query_text = f"?{urlencode(query)}" if query else ""
        request = Request(
            f"{API_BASE}{path}{query_text}",
            method="GET",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self._token}",
                "X-GitHub-Api-Version": API_VERSION,
            },
        )
        try:
            body = self._transport(request, 10.0)
        except HTTPError as error:
            if error.code in (401, 403):
                raise GateError("拒绝发布：GITHUB_TOKEN 没有读取 Actions/Environment 审查记录的权限。") from error
            raise GateError(f"拒绝发布：GitHub API 返回 HTTP {error.code}，无法核验安全审查。") from error
        except (URLError, TimeoutError, OSError) as error:
            raise GateError("拒绝发布：GitHub API 查询失败或超时，无法核验安全审查。") from error
        try:
            return json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise GateError("拒绝发布：GitHub API 返回的审查记录不是有效 JSON。") from error

    @property
    def repository_path(self) -> str:
        owner, repository = self._repository.split("/", 1)
        return f"/repos/{quote(owner, safe='')}/{quote(repository, safe='')}"


def require_mapping(value: Any, message: str) -> Mapping[str, Any]:
    if not isinstance(value, dict):
        raise GateError(message)
    return value


def require_nonempty_string(value: Any, message: str) -> str:
    if not isinstance(value, str) or not value:
        raise GateError(message)
    return value


def require_positive_int(value: Any, message: str) -> int:
    if type(value) is not int or value <= 0:
        raise GateError(message)
    return value


def parse_timestamp(value: Any, message: str) -> datetime:
    if not isinstance(value, str) or not value:
        raise GateError(message)
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise GateError(message) from error
    if parsed.tzinfo is None:
        raise GateError(message)
    return parsed.astimezone(timezone.utc)


@dataclass(frozen=True)
class Identity:
    """GitHub 用户的不可变数值身份与仅用于证据摘要的登录名。"""

    id: int
    login: str


@dataclass(frozen=True)
class EnvironmentPolicy:
    """当前独立审查 Environment 的不可替代保护属性。"""

    id: int
    updated_at: datetime
    reviewer_ids: frozenset[int]


def require_identity(value: Any, message: str) -> Identity:
    user = require_mapping(value, message)
    return Identity(
        id=require_positive_int(user.get("id"), message),
        login=require_nonempty_string(user.get("login"), message),
    )


def validate_environment(environment: Any) -> EnvironmentPolicy:
    metadata = require_mapping(environment, "拒绝发布：Environment 元数据格式无效。")
    if metadata.get("name") != SECURITY_REVIEW_ENVIRONMENT:
        raise GateError("拒绝发布：Environment 元数据不属于独立安全审查环境。")
    environment_id = require_positive_int(metadata.get("id"), "拒绝发布：Environment 缺少可信数值 id。")
    updated_at = parse_timestamp(metadata.get("updated_at"), "拒绝发布：Environment 缺少可信 updated_at。")
    if metadata.get("can_admins_bypass") is not False:
        raise GateError(
            "拒绝发布：无法证明 independent-security-review 已关闭管理员绕过；"
            "需要可读取 can_admins_bypass=false 的 Environment 元数据。"
        )
    rules = metadata.get("protection_rules")
    if not isinstance(rules, list):
        raise GateError("拒绝发布：Environment 未返回保护规则，无法证明需要人工审查。")
    reviewer_rules = [rule for rule in rules if isinstance(rule, dict) and rule.get("type") == "required_reviewers"]
    if not reviewer_rules:
        raise GateError("拒绝发布：Environment 未配置 required_reviewers 保护规则。")
    if len(reviewer_rules) != 1:
        raise GateError("拒绝发布：Environment 返回多个 required_reviewers 规则，无法确定当前审批人集合。")
    rule = reviewer_rules[0]
    if rule.get("prevent_self_review") is not True:
        raise GateError("拒绝发布：Environment 未启用 prevent_self_review。")
    reviewers = rule.get("reviewers")
    if not isinstance(reviewers, list) or not reviewers:
        raise GateError("拒绝发布：Environment 的 required_reviewers 为空。")
    reviewer_ids: set[int] = set()
    for reviewer_entry in reviewers:
        reviewer = require_mapping(reviewer_entry, "拒绝发布：required_reviewers 条目格式无效。")
        if reviewer.get("type") != "User":
            raise GateError("拒绝发布：当前门禁不能核验团队审批成员，required_reviewers 只能配置 User。")
        reviewer_ids.add(require_positive_int(
            require_mapping(reviewer.get("reviewer"), "拒绝发布：required_reviewers 缺少用户身份。").get("id"),
            "拒绝发布：required_reviewers 缺少可信用户 id。",
        ))
    return EnvironmentPolicy(environment_id, updated_at, frozenset(reviewer_ids))


def has_independent_approval(
    approvals: Any,
    environment: EnvironmentPolicy,
    actor: Identity,
    triggering_actor: Identity,
) -> Identity | None:
    if not isinstance(approvals, list):
        raise GateError("拒绝发布：审批历史格式无效。")
    for approval in approvals:
        if not isinstance(approval, dict) or approval.get("state") != "approved":
            continue
        environments = approval.get("environments")
        if not isinstance(environments, list) or not any(
            isinstance(approval_environment, dict)
            and approval_environment.get("id") == environment.id
            and approval_environment.get("name") == SECURITY_REVIEW_ENVIRONMENT
            for approval_environment in environments
        ):
            continue
        try:
            reviewer = require_identity(approval.get("user"), "拒绝发布：审批记录缺少可信审批人身份。")
        except GateError:
            continue
        if reviewer.id not in environment.reviewer_ids:
            continue
        if reviewer.id == actor.id or reviewer.id == triggering_actor.id:
            continue
        return reviewer
    return None


def select_workflow_id(api: GitHubApi) -> int:
    workflow = api.get_json(f"{api.repository_path}/actions/workflows/{quote(SECURITY_REVIEW_WORKFLOW, safe='')}")
    metadata = require_mapping(workflow, "拒绝发布：安全审查工作流元数据格式无效。")
    if metadata.get("path") != SECURITY_REVIEW_WORKFLOW_PATH:
        raise GateError("拒绝发布：查询到的工作流路径不是 .github/workflows/security-review.yml。")
    if metadata.get("state") != "active":
        raise GateError("拒绝发布：安全审查工作流尚未启用。")
    return require_positive_int(metadata.get("id"), "拒绝发布：安全审查工作流缺少可信 workflow id。")


def matching_runs(api: GitHubApi, workflow_id: int, target_sha: str) -> list[Mapping[str, Any]]:
    candidates: list[Mapping[str, Any]] = []
    for page in range(1, MAX_RUN_PAGES + 1):
        response = api.get_json(
            f"{api.repository_path}/actions/workflows/{workflow_id}/runs",
            {
                "head_sha": target_sha,
                "event": EXPECTED_EVENT,
                "per_page": PER_PAGE,
                "page": page,
            },
        )
        data = require_mapping(response, "拒绝发布：工作流运行列表格式无效。")
        runs = data.get("workflow_runs")
        if not isinstance(runs, list):
            raise GateError("拒绝发布：工作流运行列表缺少 workflow_runs。")
        for run in runs:
            if not isinstance(run, dict):
                continue
            if (
                run.get("workflow_id") == workflow_id
                and run.get("head_sha") == target_sha
                and run.get("event") == EXPECTED_EVENT
                and run.get("head_branch") == EXPECTED_BRANCH
            ):
                candidates.append(run)
        total_count = data.get("total_count")
        if type(total_count) is not int or total_count < 0:
            raise GateError("拒绝发布：工作流运行列表缺少可信 total_count。")
        if len(runs) < PER_PAGE or page * PER_PAGE >= total_count:
            return candidates
    raise GateError("拒绝发布：安全审查运行分页超过上限，无法完整核验记录。")


def select_latest_run(runs: list[Mapping[str, Any]]) -> Mapping[str, Any]:
    if not runs:
        raise GateError("拒绝发布：没有找到该提交在 main 的独立安全审查运行。")
    ranked: list[tuple[tuple[int, int], Mapping[str, Any]]] = []
    for run in runs:
        run_id = require_positive_int(run.get("id"), "拒绝发布：安全审查运行缺少可信 run id。")
        run_number = require_positive_int(
            run.get("run_number"), "拒绝发布：安全审查运行缺少可信 run_number，无法选择最新运行。"
        )
        ranked.append(((run_number, run_id), run))
    latest_key = max(key for key, _ in ranked)
    latest = [run for key, run in ranked if key == latest_key]
    if len(latest) != 1:
        raise GateError("拒绝发布：无法唯一确定最新安全审查运行。")
    return latest[0]


def verify_release_security_review(
    target_sha: str,
    context: GateContext,
    transport: Transport = default_transport,
) -> str:
    """返回不含令牌的审查证据摘要，任一不可证实条件均拒绝发布。"""

    if not SHA_PATTERN.fullmatch(target_sha):
        raise GateError("拒绝发布：门禁要求完整的 40 位提交 SHA。")
    canonical_sha = target_sha.lower()
    context.validate(canonical_sha)
    api = GitHubApi(context.repository, context.token, transport)

    environment = api.get_json(
        f"{api.repository_path}/environments/{quote(SECURITY_REVIEW_ENVIRONMENT, safe='')}"
    )
    environment_policy = validate_environment(environment)
    workflow_id = select_workflow_id(api)
    latest_run = select_latest_run(matching_runs(api, workflow_id, canonical_sha))
    if latest_run.get("path") != SECURITY_REVIEW_WORKFLOW_PATH:
        raise GateError("拒绝发布：最新运行的工作流路径不匹配。")
    run_id = require_positive_int(latest_run.get("id"), "拒绝发布：安全审查运行缺少可信 run id。")
    if latest_run.get("status") != "completed" or latest_run.get("conclusion") != "success":
        raise GateError("拒绝发布：最新独立安全审查运行尚未成功完成。")
    attempt = latest_run.get("run_attempt")
    if type(attempt) is not int or attempt < 1:
        raise GateError("拒绝发布：最新安全审查运行缺少可信 run_attempt，无法关联审批。")
    if attempt != 1:
        raise GateError(
            "拒绝发布：最新安全审查运行已重跑（run_id=" + str(run_id) + "）；"
            "GitHub 审批历史不含 attempt 关联，不能将旧批准用于新 attempt。"
        )
    run_created_at = parse_timestamp(
        latest_run.get("created_at"), "拒绝发布：最新安全审查运行缺少可信 created_at。"
    )
    if environment_policy.updated_at > run_created_at:
        raise GateError("拒绝发布：Environment 在该运行创建后变更，当前保护规则不能证明旧审查。")
    actor = require_identity(latest_run.get("actor"), "拒绝发布：安全审查运行缺少 actor，无法排除自审。")
    triggering_actor = require_identity(
        latest_run.get("triggering_actor"), "拒绝发布：安全审查运行缺少 triggering_actor，无法排除重跑发起人自审。"
    )
    approvals = api.get_json(f"{api.repository_path}/actions/runs/{run_id}/approvals")
    reviewer = has_independent_approval(approvals, environment_policy, actor, triggering_actor)
    if reviewer is None:
        raise GateError("拒绝发布：最新成功运行没有来自当前独立审查者的 approved Environment 审批记录。")
    return (
        f"安全审查已验证：sha={canonical_sha} workflow_id={workflow_id} "
        f"run_id={run_id} run_attempt=1 environment={SECURITY_REVIEW_ENVIRONMENT} reviewer={reviewer.login}"
    )


def main(arguments: list[str], environment: Mapping[str, str]) -> int:
    if len(arguments) != 1:
        print("usage: verify-release-security-review.py <40-character-commit-sha>", file=sys.stderr)
        return 2
    try:
        evidence = verify_release_security_review(arguments[0], GateContext.from_environment(environment))
    except GateError as error:
        print(str(error), file=sys.stderr)
        return 1
    print(evidence)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:], os.environ))
