import unittest
from check_repository import code_only

class GuardTests(unittest.TestCase):
    def test_nested_comments(self):
        self.assertNotIn("sorry", code_only("/- nested /- sorry -/ comment -/ theorem x := True"))
    def test_real_hole_survives(self):
        self.assertIn("sorry", code_only("theorem x := by /- comment -/ sorry"))
    def test_strings_and_lines(self):
        self.assertNotIn("sorry", code_only('def x := "sorry" -- sorry\n'))

if __name__ == '__main__': unittest.main()
