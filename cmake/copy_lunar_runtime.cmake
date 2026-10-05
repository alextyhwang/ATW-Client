# Historical installations, downloaded JREs and launcher caches are not runtime
# dependencies. Preserve package-local account/settings changes between builds.
foreach(part offline licenses textures ui game-cache)
    if(EXISTS "${SOURCE}/${part}")
        file(COPY "${SOURCE}/${part}" DESTINATION "${DESTINATION}")
    endif()
endforeach()
foreach(part profiles settings)
    if(NOT EXISTS "${DESTINATION}/${part}" AND EXISTS "${SOURCE}/${part}")
        file(COPY "${SOURCE}/${part}" DESTINATION "${DESTINATION}")
    endif()
endforeach()
