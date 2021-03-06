/**
Promote build to next steps, waiting for user input

Parameters:

* cfg jplConfig class object
* String message User input message, defaults to "Promote Build"
* String description User input description, defaults to "Check to promote the build, leave uncheck to finish the build without promote"

cfg usage:

* cfg.promoteBuild
*/
def call(cfg, String message = 'Promote Build', String description = 'Check to promote the build, leave uncheck to finish the build without promote') {
    jplConfig.checkInitializationStatus(cfg)
    try {
        cfg.promoteBuild.enabled = false
        timeout(time: cfg.promoteBuild.timeoutHours, unit: 'HOURS') {
            cfg.promoteBuild.enabled = input(
                id: 'promoteBuild', message: message,
                parameters: [[$class: 'BooleanParameterDefinition', defaultValue: false, description: description, name: 'promoteBuild']]
            )
        }
    } catch(err) { // timeout reached or input false
        currentBuild.result = 'ABORTED'
        error ('jplPromoteBuild: Timeout reached / User aborted. Aborting')
    }
    if (cfg.promoteBuild.enabled) {
        echo 'Promote this build'
    }
    else {
        echo 'Does not promote this build'
    }
}
