import unittest

import portal


class CentralStableOnlyTest(unittest.TestCase):
    def test_direct_central_operations_reject_prereleases_and_snapshots_before_network_or_crypto(self):
        for version in ("1.2.3-alpha.1", "1.2.3-beta.1", "1.2.3-rc.1", "1.2.3-SNAPSHOT"):
            def unexpected(*args, **kwargs):
                self.fail("A development version reached Central")
            with self.subTest(version=version), self.assertRaises(ValueError):
                portal.upload(None, None, {"version": version}, None, "", opener=unexpected)
            with self.subTest(version=version), self.assertRaises(ValueError):
                portal.publish(None, {"version": version}, None, "", opener=unexpected)


if __name__ == "__main__":
    unittest.main()
