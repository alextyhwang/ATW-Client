if(NOT DEFINED JAR_EXECUTABLE)
    message(FATAL_ERROR "JAR_EXECUTABLE is required")
endif()

if(NOT DEFINED LUNAR_JAR)
    message(FATAL_ERROR "LUNAR_JAR is required")
endif()

if(NOT DEFINED OVERLAY_DIR)
    message(FATAL_ERROR "OVERLAY_DIR is required")
endif()

if(NOT EXISTS "${LUNAR_JAR}")
    message(FATAL_ERROR "Lunar jar not found: ${LUNAR_JAR}")
endif()

if(NOT EXISTS "${OVERLAY_DIR}/assets/lunar/logo")
    message(FATAL_ERROR "Lunar branding overlay not found: ${OVERLAY_DIR}")
endif()

execute_process(
    COMMAND "${JAR_EXECUTABLE}" uf "${LUNAR_JAR}"
        assets/lunar/logo/logo-branding-215x33.png
        assets/lunar/logo/logo-branding-light-215x33.png
        assets/lunar/logo/logo-branding-thick-300x60.png
        assets/lunar/logo/logo-branding-thick-440x88.png
        assets/lunar/logo/logo-branding-thick-logo-390x60.png
    WORKING_DIRECTORY "${OVERLAY_DIR}"
    RESULT_VARIABLE patch_result
)

if(NOT patch_result EQUAL 0)
    message(FATAL_ERROR "Failed to patch Lunar branding assets into ${LUNAR_JAR}")
endif()
