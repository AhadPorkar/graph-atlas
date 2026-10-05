package ir.graph.repo.core.domain;
public final class RepositoryException extends RuntimeException {
    public final int status; public final String code;
    public RepositoryException(int status,String code,String message){super(message);this.status=status;this.code=code;}
    public static RepositoryException bad(String message){return new RepositoryException(400,"BAD_REQUEST",message);}
    public static RepositoryException missing(){return new RepositoryException(404,"NOT_FOUND","The requested resource was not found.");}
}
