"""Publication must never silently attach a verified package to an existing tag."""
import json
import subprocess
import unittest
from unittest.mock import patch

import publish_release


class TagBoundaryTests(unittest.TestCase):
    def test_existing_tag_cannot_select_other_source_or_indirect_reference(self):
        for kind, commit in (("commit", "b" * 40), ("tag", "a" * 40)):
            result = {"ref": "refs/tags/v1.0.0", "object": {"type": kind, "sha": commit}}
            with patch.object(publish_release, "gh", return_value=json.dumps(result)):
                with self.assertRaises(ValueError):
                    publish_release.verify_existing_tag("owner/repo", "v1.0.0", "a" * 40)

    def test_explicit_existing_tag_is_read_only_and_matches_verified_source(self):
        result = {"ref": "refs/tags/v1.0.0", "object": {"type": "commit", "sha": "a" * 40}}
        with patch.object(publish_release, "gh", return_value=json.dumps(result)) as api:
            publish_release.verify_existing_tag("owner/repo", "v1.0.0", "a" * 40)
            self.assertNotIn("POST", api.call_args.args)
            self.assertNotIn("PATCH", api.call_args.args)

    def test_existing_release_without_a_tag_cannot_be_retargeted(self):
        for draft in (True, False):
            with patch.object(publish_release, "gh", return_value=json.dumps([[], [{"tag_name": "v1.0.0", "draft": draft}]])) as api:
                with self.assertRaises(ValueError):
                    publish_release.require_new_version("owner/repo", "v1.0.0")
                self.assertNotIn("POST", api.call_args.args)

    def test_existing_tag_conflict_is_not_ignored_or_reused(self):
        with patch.object(publish_release, "gh", side_effect=subprocess.CalledProcessError(1, "gh")) as api:
            with self.assertRaises(subprocess.CalledProcessError):
                publish_release.reserve_tag("owner/repo", "v1.0.0", "a" * 40)
        self.assertEqual(api.call_count, 1)
        self.assertNotIn("PATCH", api.call_args.args)
        self.assertNotIn("DELETE", api.call_args.args)

    def test_server_response_must_confirm_the_verified_source(self):
        for result in ({"ref": "refs/tags/v1.0.0", "object": {"sha": "b" * 40}},
                       {"ref": "refs/tags/other", "object": {"sha": "a" * 40}}):
            with patch.object(publish_release, "gh", return_value=json.dumps(result)):
                with self.assertRaises(ValueError):
                    publish_release.reserve_tag("owner/repo", "v1.0.0", "a" * 40)

    def test_new_tag_selecting_verified_source_is_accepted(self):
        result = {"ref": "refs/tags/v1.0.0", "object": {"sha": "a" * 40}}
        with patch.object(publish_release, "gh", return_value=json.dumps(result)):
            publish_release.reserve_tag("owner/repo", "v1.0.0", "a" * 40)


if __name__ == "__main__":
    unittest.main()
