"""Validate only the expected public certificate before any private signing secret is used."""
import argparse
import pathlib
from signatures import PublicVerifier

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("public_key", type=pathlib.Path)
    args = parser.parse_args()
    with PublicVerifier(args.public_key):
        pass
    print("Approved public signing certificate is present, unexpired and unrevoked")
