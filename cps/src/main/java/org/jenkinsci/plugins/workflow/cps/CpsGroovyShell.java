package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.CpsTransformer;
import com.cloudbees.groovy.cps.NonCPS;
import com.cloudbees.groovy.cps.SandboxCpsTransformer;
import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import jenkins.model.Jenkins;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import java.io.IOException;

/**
 * {@link GroovyShell} with additional tweaks necessary to run {@link CpsScript}
 *
 * @author Kohsuke Kawaguchi
 */
class CpsGroovyShell extends GroovyShell {
    private final CpsFlowExecution execution;

    CpsGroovyShell(CpsFlowExecution execution) {
        super(makeClassLoader(),new Binding(),makeConfig(execution));
        this.execution = execution;
    }

    private static ClassLoader makeClassLoader() {
        Jenkins j = Jenkins.getInstance();
        return j!=null ? j.getPluginManager().uberClassLoader : CpsGroovyShell.class.getClassLoader();
    }

    private static CompilerConfiguration makeConfig(CpsFlowExecution execution) {
        ImportCustomizer ic = new ImportCustomizer();
        ic.addStarImports(NonCPS.class.getPackage().getName());
        ic.addStarImports("hudson.model","jenkins.model");

        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(ic);
        cc.addCompilationCustomizers(execution.isSandbox() ? new SandboxCpsTransformer() : new CpsTransformer());
        cc.setScriptBaseClass(CpsScript.class.getName());
        return cc;
    }

    /**
     * When actually running {@link CpsScript}, it has to get some additional variables configured.
     */
    @Override
    public Object evaluate(GroovyCodeSource codeSource) throws CompilationFailedException {
        Script script = parse(codeSource);
        script.setBinding(getContext());

        if (script instanceof CpsScript) {
            CpsScript cs = (CpsScript) script;
            cs.execution = execution;
            try {
                cs.initialize();
            } catch (IOException e) {
                // TODO: write a library to let me throw this
                throw new RuntimeException(e);
            }
        }

        // this method might slow magic CpsCallableInvocation
        return script.run();
    }
}
