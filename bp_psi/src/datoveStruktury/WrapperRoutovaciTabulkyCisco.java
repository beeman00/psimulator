package datoveStruktury;

import vyjimky.WrapperException;
import java.util.ArrayList;
import java.util.List;
import pocitac.AbstractPocitac;
import pocitac.CiscoPocitac;
import pocitac.SitoveRozhrani;

/** 
 * Trida reprezentujici wrapper nad routovaci tabulkou pro system cisco.
 * Tez bude sefovat zmenu v RT dle vlastnich rozhrani.
 * Cisco samo o sobe ma tez 2 tabulky: <br />
 *      1. zadane uzivatelem (tato trida) <br />
 *      2. vypocitane routy z tabulky c. 1 (trida RoutovaciTabulka)
 * @author haldyr
 */
public class WrapperRoutovaciTabulkyCisco {

    /**
     * Jednotlive radky wrapperu.
     */
    private List<CiscoZaznam> radky;
    AbstractPocitac pc;
    /**
     * Odkaz na routovaci tabulku, ktera je wrapperem ovladana.
     */
    RoutovaciTabulka routovaciTabulka;
    /**
     * ochrana proti smyckam v routovaci tabulce.
     * Kdyz to projede 50 rout, tak se hledani zastavi s tim, ze smula..
     */
    int citac = 0;

    public WrapperRoutovaciTabulkyCisco(AbstractPocitac pc) {
        radky = new ArrayList<CiscoZaznam>();
        this.pc = pc;
        this.routovaciTabulka = pc.routovaciTabulka;
    }

    /**
     * Vnitrni trida pro reprezentaci CiscoZaznamu ve wrapperu.
     * Adresat neni null, ale bud rozhrani nebo brana je vzdy null.
     */
    public class CiscoZaznam {

        // ip route 192.168.2.0 255.255.255.192 192.168.2.126
        // ip route 192.168.100.0 255.255.255.0 FastEthernet0/1
        private IpAdresa adresat; // s maskou
        private IpAdresa brana;
        private SitoveRozhrani rozhrani;
        private boolean connected = false;

        private CiscoZaznam(IpAdresa adresat, IpAdresa brana) {
            this.adresat = adresat;
            this.brana = brana;
        }

        private CiscoZaznam(IpAdresa adresat, SitoveRozhrani rozhrani) {
            this.adresat = adresat;
            this.rozhrani = rozhrani;
        }

        /**
         * Pouze pro ucely vypisu RT!!!
         * @param adresat
         * @param brana
         * @param rozhrani
         */
        private CiscoZaznam(IpAdresa adresat, IpAdresa brana, SitoveRozhrani rozhrani) {
            this.adresat = adresat;
            this.brana = brana;
            this.rozhrani = rozhrani;
        }

        public IpAdresa getAdresat() {
            return adresat;
        }

        public IpAdresa getBrana() {
            return brana;
        }

        public SitoveRozhrani getRozhrani() {
            return rozhrani;
        }

        private void setConnected() {
            this.connected = true;
        }

        public boolean isConnected() {
            return connected;
        }

        @Override
        public String toString() {
            String s = adresat.vypisAdresu() + " " + adresat.vypisMasku() + " ";
            if (brana == null) {
                s += rozhrani.jmeno;
            } else {
                s += brana.vypisAdresu();
            }
            return s;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj.getClass() != CiscoZaznam.class) {
                return false;
            }

            if (adresat.equals(((CiscoZaznam) obj).adresat)) {
                if (brana != null && ((CiscoZaznam) obj).brana != null) {
                    if (brana.equals(((CiscoZaznam) obj).brana)) {
                        return true;
                    }
                } else {
                    if (rozhrani.jmeno.equalsIgnoreCase(((CiscoZaznam) obj).rozhrani.jmeno)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + (this.adresat != null ? this.adresat.hashCode() : 0);
            hash = 37 * hash + (this.brana != null ? this.brana.hashCode() : 0);
            hash = 37 * hash + (this.rozhrani != null ? this.rozhrani.hashCode() : 0);
            return hash;
        }
    }

    /**
     * Tato metoda bude aktualizovat RoutovaciTabulku dle tohoto wrapperu.
     */
    public void update() {
        // smazu RT
        routovaciTabulka.smazVsechnyZaznamy();

        // nastavuju citac
        citac = 0;

        // pridam routy na nahozena rozhrani
        for (SitoveRozhrani iface : pc.rozhrani) {
            if (iface.jeNahozene()) {
                routovaciTabulka.pridejZaznam(iface.ip, iface, true);
            }
        }

        // propocitam a pridam routy s prirazenyma rozhranima
        for (CiscoZaznam zaznam : radky) {
            if (zaznam.rozhrani != null) { // kdyz to je na rozhrani
                routovaciTabulka.pridejZaznam(zaznam.adresat, zaznam.rozhrani);
            } else { // kdyz to je na branu
                SitoveRozhrani odeslat = najdiRozhraniProBranu(zaznam.brana);
                if (odeslat != null) {
//                    System.out.println("nasel jsem pro "+zaznam.adresat.vypisAdresu() + " rozhrani "+odeslat.jmeno);
                    routovaciTabulka.pridejZaznamBezKontrol(zaznam.adresat, zaznam.brana, odeslat);
                } else {
//                    System.out.println("nenasel jsem pro "+ zaznam);
                }
            }
        }
    }
    
    /**
     * Vrati rozhrani, na ktere se ma odesilat, kdyz je zaznam na branu.
     * Tato metoda pocita s tim, ze v RT uz jsou zaznamy pro nahozena rozhrani.
     * @param brana
     * @return kdyz nelze nalezt zadne rozhrani, tak vrati null
     */
    SitoveRozhrani najdiRozhraniProBranu(IpAdresa brana) {
        SitoveRozhrani iface = null;

        citac++;
        if (citac >= 50) {
            return null; // ochrana proti smyckam
        }
        for (int i = radky.size() - 1; i >= 0; i--) { // prochazim opacne (tedy vybiram s nevyssim poctem jednicek)

            // kdyz to je na rozsah vlastniho rozhrani
            //mrknout se jestli to tady vadi!
            iface = routovaciTabulka.najdiSpravnyRozhrani(brana);
            if (iface != null) {
                return iface;
            }

            // kdyz to je na branu
            CiscoZaznam zaznam = radky.get(i);
            if (brana.jeVRozsahu(zaznam.adresat)) {
                if (zaznam.rozhrani != null) { // 172.18.1.0 255.255.255.0 FastEthernet0/0
                    return zaznam.rozhrani;
                }
                return najdiRozhraniProBranu(zaznam.brana);
            }
        }
        return null;
    }

    /**
     * Pridava do wrapperu novou routu na branu.
     * @param adresa
     * @param brana
     */
    public void pridejZaznam(IpAdresa adresa, IpAdresa brana) {
        CiscoZaznam z = new CiscoZaznam(adresa, brana);
        pridejZaznam(z);
    }

    /**
     * Pridava do wrapperu novou routu na rozhrani.
     * @param adresa
     * @param rozhrani
     */
    public void pridejZaznam(IpAdresa adresa, SitoveRozhrani rozhrani) {
        CiscoZaznam z = new CiscoZaznam(adresa, rozhrani);
        pridejZaznam(z);
    }

    /**
     * Prida do wrapperu novou routu na rozhrani. Pote updatuje RT je-li potreba.
     * V teto metode se kontroluje, zda adresat je cislem site.
     * @param zaznam, ktery chci vlozit
     */
    private void pridejZaznam(CiscoZaznam zaznam) {

        if (!zaznam.getAdresat().jeCislemSite()) { // vyjimka prevazne pro nacitani z konfiguraku
            throw new WrapperException("Adresa " + zaznam.getAdresat().vypisAdresu() + " neni cislem site!");
        }

        for (CiscoZaznam z : radky) { // zaznamy ulozene v tabulce se uz znovu nepridavaji
            if (zaznam.equals(z)) {
                return;
            }
        }

        radky.add(dejIndexPozice(zaznam, true), zaznam);

        update();
    }

    private void pridejRTZaznam(RoutovaciTabulka.Zaznam zaznam) {
        CiscoZaznam ciscozaznam = new CiscoZaznam(zaznam.getAdresat(), zaznam.getBrana(), zaznam.getRozhrani());
        if (zaznam.jePrimoPripojene()) {
            ciscozaznam.setConnected();
        }
        radky.add(dejIndexPozice(ciscozaznam, false), ciscozaznam);
    }

    // 1/ no ip route IP maska + a volitelne treti zaznam, kdyz je bez 3.zaznamu a tak se smaze vsechno co sedi dle 1. a 2.
    //        + prekontrolovat vsechny routy a zrusit jim pripadne rozhrani (kdyz budou nedostupny)
    /**
     * Smaze zaznam z wrapperu + aktualizuje RT. Rozhrani maze podle jmena!
     * Muze byt zadana bud adresa nebo adresa+brana nebo adresa+rozhrani.
     * @param adresa
     * @param brana
     * @param rozhrani
     * @return 0 = ok, 1 = nic se nesmazalo
     */
    public int smazZaznam(IpAdresa adresa, IpAdresa brana, SitoveRozhrani rozhrani) {
        int i = 0;

        if (adresa == null) {
            return 1;
        }
        if (brana != null && rozhrani != null) {
            return 1;
        }

        // maze se zde pres specialni seznam, inac to hazi concurrent neco vyjimku..
        List<CiscoZaznam> smazat = new ArrayList();

        for (CiscoZaznam z : radky) {

            if (!z.adresat.equals(adresa)) {
                continue;
            }

            if (brana == null && rozhrani == null) {
                smazat.add(radky.get(i));

            } else if (brana != null && rozhrani == null) {
                if (z.brana.equals(brana)) {
                    smazat.add(radky.get(i));
                }
            } else if (brana == null && rozhrani != null) {
                if (z.rozhrani.jmeno.equals(rozhrani.jmeno)) {
                    smazat.add(radky.get(i));
                }
            }
        }

        for (CiscoZaznam zaznam : smazat) {
            radky.remove(zaznam);
        }

        update();

        return 0;
    }
    
    /**
     * Smaze vsechny zaznamy ve wrapperu + zaktualizuje RT
     * Prikaz 'clear ip route *'
     */
    public void smazVsechnyZaznamy() {
        radky.clear();
        update();
    }

    /**
     * Vrati pozici, na kterou se bude pridavat zaznam do wrapperu.
     * Je to razeny dle integeru cile.
     * @param pridavany, zaznam, ktery chceme pridat
     * @param nejminBituVMasce rika, jestli chceme radit nejdrive zaznamy maskou o mensim poctu 1,
     * pouziva se pri normalnim vkladani do wrapperu, false pro vypis RT
     * @return
     */
    private int dejIndexPozice(CiscoZaznam pridavany, boolean nejminBituVMasce) {
        int i = 0;
        for (CiscoZaznam cz : radky) {
            if (jeMensiIP(pridavany.adresat, cz.adresat, nejminBituVMasce)) {
                break;
            }
            i++;
        }
        return i;
    }

    /**
     * Vrati true, pokud je prvni adresa mensi nez druha, pokud se rovnaji, tak rozhoduje maska.
     * @param prvni
     * @param druha
     * @return
     */
    private boolean jeMensiIP(IpAdresa prvni, IpAdresa druha, boolean nejminBituVMasce) {

        // kdyz maj stejny IP a ruzny masky
        if (prvni.vypisAdresu().equals(druha.vypisAdresu())) {
            if (nejminBituVMasce) { // pro pridani do wrapperu
                if (prvni.pocetBituMasky() < druha.pocetBituMasky()) {
                    return true;
                }
            } else { // pro vypis RT
                if (prvni.pocetBituMasky() > druha.pocetBituMasky()) {
                    return true;
                }
            }
        }
        if (prvni.dejLongIP() < druha.dejLongIP()) {
            return true;
        }
        return false;
    }

    /**
     * Vrati CiscoZaznam na indexu.
     * @param index
     * @return
     */
    public CiscoZaznam vratZaznam(int index) {
        return radky.get(index);
    }

    /**
     * Vrati pocet zaznamu ve wrapperu.
     * @return
     */
    public int size() {
        return radky.size();
    }

    /**
     * Pro vypis pres 'sh run'
     * @return
     */
    public String vypisRunningConfig() {
        String s = "";
        for (CiscoZaznam z : radky) {
            s += "ip route " + z + "\n";
        }
        return s;
    }

    /**
     * Vrati vypis routovaci tabulky.
     * Kasle se na tridni vypisy pro adresaty ze A rozsahu, protoze se v laborce takovy rozsah nepouziva.
     * @return
     */
    public String vypisRT() {
        String s = "";

        s += "Codes: C - connected, S - static\n\n";
        boolean defaultGW = false;
        for (int i = 0; i < ((CiscoPocitac) pc).getWrapper().size(); i++) {
            if (((CiscoPocitac) pc).getWrapper().vratZaznam(i).adresat.equals(new IpAdresa("0.0.0.0", 0))) {
                defaultGW = true;
            }
        }

        s += "Gateway of last resort is ";
        if (defaultGW) {
            s += "0.0.0.0 to network 0.0.0.0\n\n";
        } else {
            s += "not set\n\n";
        }

        // vytvarim novy wrapperu kvuli zabudovanemu razeni
        WrapperRoutovaciTabulkyCisco wrapper = new WrapperRoutovaciTabulkyCisco(pc);
        for (int i = 0; i < routovaciTabulka.pocetZaznamu(); i++) {
            wrapper.pridejRTZaznam(routovaciTabulka.vratZaznam(i));
        }

        for (CiscoZaznam czaznam : wrapper.radky) {
            s += vypisZaznamDoRT(czaznam);
        }
        
        return s;
    }

    /**
     * Vrati vypis cisco zaznamu ve spravnem formatu pro RT
     * @param zaznam
     * @return
     */
    private String vypisZaznamDoRT(CiscoZaznam zaznam) {
        String s = "";

        if (zaznam.isConnected()) { //C       21.21.21.0 is directly connected, FastEthernet0/0
            s += "C       " + zaznam.getAdresat().vypisCisloSite() + "/" + zaznam.getAdresat().pocetBituMasky() + " is directly connected, " + zaznam.getRozhrani().jmeno + "\n";
        } else { //S       18.18.18.0 [1/0] via 51.51.51.9
            if (zaznam.getAdresat().equals(new IpAdresa("0.0.0.0", 0))) {
                s += "S*      ";
            } else {
                s += "S       ";
            }
            s += zaznam.getAdresat().vypisAdresu() + "/" + zaznam.getAdresat().pocetBituMasky();
            if (zaznam.getBrana() != null) {
                s += " [1/0] via " + zaznam.getBrana().vypisAdresu();
            } else {
                s += " is directly connected, " + zaznam.getRozhrani().jmeno;
            }
            s += "\n";
        }

        return s;
    }
}