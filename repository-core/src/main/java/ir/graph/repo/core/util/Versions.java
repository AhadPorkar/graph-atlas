package ir.graph.repo.core.util;

import ir.graph.repo.core.domain.RepositoryException;
import java.math.BigInteger;
import java.util.*;
public final class Versions {
    private Versions() {
    }
    public static String nuget(String value) {
        String[] p=value.toLowerCase(Locale.ROOT).split("\\+",2)[0].split("-",2);
        String[] numbers=p[0].split("\\.");
        if(numbers.length<1||numbers.length>4)throw RepositoryException.bad("Invalid NuGet version");
        List<String> core=new ArrayList<>();
        for(String n:numbers) {
            if(!n.matches("[0-9]+"))throw RepositoryException.bad("Invalid NuGet version");
            core.add(new BigInteger(n).toString());
        }
        while(core.size()<3)core.add("0");
        if(core.size()==4&&core.get(3).equals("0"))core.remove(3);
        if(p.length==2&&!p[1].matches("[0-9a-z-]+(\\.[0-9a-z-]+)*"))throw RepositoryException.bad("Invalid prerelease version");
        return String.join(".",core)+(p.length==2?"-"+p[1]:"");
    }
    public static int compare(String left,String right) {
        String[] a=left.split("\\+",2)[0].split("-",2),b=right.split("\\+",2)[0].split("-",2);
        String[] x=a[0].split("\\."),y=b[0].split("\\.");
        for(int i=0; i<Math.max(x.length,y.length); i++) {
            String v=i<x.length?x[i]:"0",w=i<y.length?y[i]:"0";
            int c=component(v,w);
            if(c!=0)return c;
        }
        if(a.length!=b.length)return a.length==1?1:-1;
        if(a.length==1)return 0;
        x=a[1].split("\\.");
        y=b[1].split("\\.");
        for(int i=0; i<Math.min(x.length,y.length); i++) {
            int c=component(x[i],y[i]);
            if(c!=0)return c;
        }
        return Integer.compare(x.length,y.length);
    }
    private static int component(String a,String b) {
        boolean an=a.matches("[0-9]+"),bn=b.matches("[0-9]+");
        if(an&&bn)return new BigInteger(a).compareTo(new BigInteger(b));
        if(an!=bn)return an?-1:1;
        return a.compareToIgnoreCase(b);
    }
    public static String pythonName(String s) {
        if(s==null||!s.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))throw RepositoryException.bad("Invalid Python package name");
        return s.toLowerCase(Locale.ROOT).replaceAll("[-_.]+","-");
    }
}
