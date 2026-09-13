cmake_minimum_required(VERSION 3.22.1)
project(switchprobridge)

add_library(switchprobridge SHARED bridge.cpp)

find_library(log-lib log)

target_link_libraries(switchprobridge ${log-lib})
