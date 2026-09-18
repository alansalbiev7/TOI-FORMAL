#!/usr/bin/env python3
"""Verify Gradle 8.10.2 wrapper against the official published digest."""
from pathlib import Path
import hashlib
r = Path(__file__).resolve().parents[1]
jar = r / 'asg-core/gradle/wrapper/gradle-wrapper.jar'
expected = '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
assert hashlib.sha256(jar.read_bytes()).hexdigest() == expected, 'Wrapper JAR checksum mismatch'
props = (jar.parent / 'gradle-wrapper.properties').read_text()
assert 'distributionSha256Sum=31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26' in props
print('Gradle wrapper and distribution digest verified')
