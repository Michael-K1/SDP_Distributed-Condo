package mk1.sdp.misc;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Pair<M,V>{

    public   M first;
    public   V second;

    public Pair(){}     //needed for marshalling


    public Pair(M v1, V v2){
        first=v1;
        second= v2;
    }
    @Override
    public int hashCode(){
        return first.hashCode()^second.hashCode();  //^ --> XOR
    }
    @Override
    public boolean equals(Object o){
        if(!(o instanceof Pair)) return false;
        Pair pair = (Pair) o;
        return this.first.equals(pair.first) && this.second.equals(pair.second);
    }
	
	public static <M,V> Pair<M,V> of(M left, V right){

        return new Pair<>(left, right);
    }
}
