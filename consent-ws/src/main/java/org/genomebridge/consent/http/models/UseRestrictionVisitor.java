package org.genomebridge.consent.http.models;

public interface UseRestrictionVisitor {

    void startChildren();
    void endChildren();
    boolean visit(UseRestriction r);

}
