import copy
import unittest

from snapshot_gate import automatic_version, successful_run


class AutomaticSnapshotTest(unittest.TestCase):
    def test_publication_version_advances_a_stable_baseline_and_preserves_development_base(self):
        for source, expected in (("5.0.0", "5.0.1-SNAPSHOT"), ("5.0.9", "5.0.10-SNAPSHOT"),
                                 ("5.1.0-alpha.1", "5.1.0-SNAPSHOT"), ("5.1.0-SNAPSHOT", "5.1.0-SNAPSHOT")):
            self.assertEqual(expected, automatic_version(source))
        for bad in ("../5.0.0", "5.0.0-snapshot", "5.0.0+mutable"):
            with self.assertRaises(ValueError):
                automatic_version(bad)

    def test_only_exact_push_source_run_and_all_completed_ci_jobs_allow_publication(self):
        run = {"id": 123, "head_sha": "a" * 40, "head_branch": "develop", "event": "push",
               "path": ".github/workflows/build.yml", "repository": {"full_name": "NuaForma/image-comparison"}}
        jobs = [{"name": name, "status": "completed", "conclusion": "success"} for name in
                ("Build (Java 17)", "Build (Java 25)", "Release preparation (disposable keys, no upload)")]
        successful_run(run, jobs, "NuaForma/image-comparison", "a" * 40, "123")
        for field, value in (("id", 124), ("head_sha", "b" * 40), ("head_branch", "main"),
                             ("event", "pull_request"), ("path", ".github/workflows/other.yml"),
                             ("repository", {"full_name": "other/fork"})):
            with self.subTest(field=field), self.assertRaises(ValueError):
                successful_run(dict(run, **{field: value}), jobs, "NuaForma/image-comparison", "a" * 40, "123")
        for index in range(3):
            for status, conclusion in (("in_progress", None), ("completed", "failure"), ("completed", "skipped")):
                bad = copy.deepcopy(jobs)
                bad[index].update(status=status, conclusion=conclusion)
                with self.assertRaises(ValueError):
                    successful_run(run, bad, "NuaForma/image-comparison", "a" * 40, "123")
        for bad in (jobs[:2], jobs + [jobs[0]]):
            with self.assertRaises(ValueError):
                successful_run(run, bad, "NuaForma/image-comparison", "a" * 40, "123")
