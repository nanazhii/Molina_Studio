cmake_minimum_required(VERSION 3.18)
project(molina_pty C)

set(CMAKE_C_STANDARD 11)

add_library(molina_pty SHARED molina_pty.c)
target_compile_options(molina_pty PRIVATE -Wall -Wextra -Os -fno-stack-protector)
target_link_options(molina_pty PRIVATE -Wl,--gc-sections -Wl,-z,max-page-size=16384)
