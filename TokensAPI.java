package me.mrbluecloud.tokens;

import java.io.File;
import java.util.HashMap;
import org.bukkit.entity.Player;

public class TokensAPI
{
  public int getTokens(Player player)
  {
    if (player == null) {
      throw new IllegalArgumentException("Player can not be null!");
    }
    return getTokens(player.getName());
  }

  public int getTokens(String player)
  {
    if (player == null) {
      throw new IllegalArgumentException("Player can not be null!");
    }

    if (tokens.instance.players.containsKey(player)) {
      return ((Integer)tokens.instance.players.get(player)).intValue();
    }

    return tokens.instance.loadTokens(new File(tokens.instance.folder, player + ".tokens"));
  }

  public boolean giveTokens(Player player, int amount)
  {
    if (player == null) {
      throw new IllegalArgumentException("Player can not be null!");
    }
    return giveTokens(player.getName(), amount);
  }

  public boolean giveTokens(String player, int amount)
  {
    if (player == null) {
      throw new IllegalArgumentException("Player can not be null!");
    }

    if (tokens.instance.players.containsKey(player)) {
    	tokens.instance.players.put(player, Integer.valueOf(amount + ((Integer)tokens.instance.players.get(player)).intValue()));
      return true;
    }

    tokens.instance.players.put(player, Integer.valueOf(tokens.instance.loadTokens(new File(tokens.instance.folder, player + ".tokens"))));
    tokens.instance.players.put(player, Integer.valueOf(amount + ((Integer)tokens.instance.players.get(player)).intValue()));
    tokens.instance.saveTokens(player);
    return true;
  }

  public boolean takeTokens(Player player, int amount)
  {
    if (player == null) {
      throw new IllegalArgumentException("Player can not be null!");
    }
    return takeTokens(player.getName(), amount);
  }

  public boolean takeTokens(String player, int amount)
  {
    if (player == null) {
      throw new IllegalArgumentException("Player can not be null!");
    }

    if (!tokens.instance.players.containsKey(player)) {
    	tokens.instance.players.put(player, Integer.valueOf(tokens.instance.loadTokens(new File(tokens.instance.folder, player + ".tokens"))));
    }

    int tokens = getTokens(player);

    int orb = Math.max(amount, tokens);
    int val = Math.min(amount, tokens);

    tokens.instance.players.put(player, Integer.valueOf(orb - val));
    tokens.instance.saveTokens(player);
    return true;
  }
}