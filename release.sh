#!/usr/bin/env bash

RELEASE_LEVEL=$1
clj -m metav.release $RELEASE_LEVEL

