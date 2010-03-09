/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package prikazy;

import java.util.List;
import pocitac.*;

/**
 * Metoda zpracujRadek(String s) prijme a zpracuje vstupni string od klienta. Ten se pak metodou rozsekej() rozseka na jednotlivy
 * slova. Pak se testuje,
 * @author neiss & haldyr
 */
public abstract class ParserPrikazu {
    
    protected  String radek;
    protected  List<String> slova; //seznam jednotlivejch slov ze vstupniho stringu
    protected AbstractPocitac pc;
    protected Konsole kon;

    public ParserPrikazu(AbstractPocitac pc,Konsole kon){
        this.pc=pc;
        this.kon=kon;
    }

    /**
     * Prijme a zpracuje vstupni string od klienta. Ten se pak metodou rozsekej() rozseka na jednotlivy slova.
     * Pak se testuje, jestli prvni slovo je nazev nejakyho podporovanyho prikazu, jestlize ne, tak se vypise
     * "command not found", jinak se preda rizeni tomu spravnymu prikazu.
     * @param s
     */
    public abstract void zpracujRadek(String s);

    /**
     * Tahlecta metoda rozseka vstupni string na jednotlivy slova (jako jejich oddelovac se bere mezera)
     */
    @Deprecated
    protected void rozsekej(){
        int i=0;
        int j=0;
        while(j<radek.length()){
            j=radek.indexOf(' ',i);
            if(j==-1)j=radek.length();
            if(i!=j) slova.add(radek.substring(i,j)); //pri pridavani nepridavam vic mezer
            i=j+1;
        }
    }

    /**
     * Tahlecta metoda rozseka vstupni string na jednotlivy slova (jako jejich oddelovac se bere mezera)
     */
    protected void rozsekejLepe() {
        String [] pole = radek.split(" ");
        for (String s : pole) {
            slova.add(s);
        }
    }

    /*
    public void setRadek(String s) {
        radek = s;
    }

    public void getRadek() {
        System.out.println(radek);
    }
     */
}
