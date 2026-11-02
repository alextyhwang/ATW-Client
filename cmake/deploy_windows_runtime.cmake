cmake_minimum_required(VERSION 3.21)

if(NOT EXISTS "${ATW_EXECUTABLE}")
    message(FATAL_ERROR "Missing executable for runtime dependency deployment")
endif()
get_filename_component(destination "${ATW_EXECUTABLE}" DIRECTORY)
file(GET_RUNTIME_DEPENDENCIES
    EXECUTABLES "${ATW_EXECUTABLE}"
    DIRECTORIES "${destination}" "${ATW_ZLIB_DIR}" "${ATW_ZLIB_DIR}/../bin"
                "${ATW_QT_DIR}" ${ATW_EXTRA_DIRS}
    RESOLVED_DEPENDENCIES_VAR resolved
    UNRESOLVED_DEPENDENCIES_VAR unresolved
    PRE_EXCLUDE_REGEXES "^api-ms-.*" "^ext-ms-.*"
    POST_EXCLUDE_REGEXES ".*[/\\][Ss][Yy][Ss][Tt][Ee][Mm]32[/\\].*")
if(unresolved)
    message(FATAL_ERROR "Unresolved runtime DLLs: ${unresolved}. Set ATW_RUNTIME_DEPENDENCY_DIRS to their directories.")
endif()
file(TO_CMAKE_PATH "$ENV{SystemRoot}" system_root)
string(TOLOWER "${system_root}/" system_prefix)
foreach(dependency IN LISTS resolved)
    # Do not redistribute OS components outside System32 either.
    string(TOLOWER "${dependency}" lower_dependency)
    string(FIND "${lower_dependency}" "${system_prefix}" system_index)
    if(NOT system_root STREQUAL "" AND system_index EQUAL 0)
        continue()
    endif()
    get_filename_component(name "${dependency}" NAME)
    if(NOT dependency STREQUAL "${destination}/${name}")
        file(COPY_FILE "${dependency}" "${destination}/${name}" ONLY_IF_DIFFERENT)
    endif()
endforeach()
