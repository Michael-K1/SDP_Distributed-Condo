package mk1.sdp.misc;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Pair<M,V>{

    public   M left;
    public   V right;

    public Pair(){}     //needed for marshalling


    public Pair(M v1, V v2){
        left =v1;
        right = v2;
    }
    @Override
    public int hashCode(){
        return left.hashCode()^ right.hashCode();  //^ --> XOR
    }
    @Override
    public boolean equals(Object o){
        if(!(o instanceof Pair)) return false;
        Pair pair = (Pair) o;
        return this.left.equals(pair.left) && this.right.equals(pair.right);
    }
	
	public static <M,V> Pair<M,V> of(M left, V right){

        return new Pair<>(left, right);
    }
}
