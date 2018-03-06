package com.gome.meidian.redis.controller;

import java.util.Arrays;
import java.util.List;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPipeline;
import redis.clients.jedis.ShardedJedisPool;
import redis.clients.jedis.Transaction;

/**
 * Redis的Java客户端Jedis的八种调用方式
 */
public class ConnectionTest {
	
	public static void main(String[] args) {
		ConnectionTest connectionTest = new ConnectionTest();
		
//		connectionTest.testNormal();
		connectionTest.testSimplePool();
	}
	
	/**
	 * 普通连接方式 Simple SET: 95.437 seconds
	 */
	public void testNormal() {
		Jedis jedis = new Jedis("127.0.0.1", 6379);
		long start = System.currentTimeMillis();
		for (int i = 0; i < 100000; i++) {
			String result = jedis.set("n" + i, "n" + i);
		}
		long end = System.currentTimeMillis();
		System.out.println("Simple SET: " + ((end - start) / 1000.0) + " seconds");
		// 销毁
		jedis.close();
	}

	/**
	 * 普通连接池方式 POOL SET: 99.789 seconds
	 * 
	 */
	public void testSimplePool() {
		JedisPool pool = new JedisPool(new JedisPoolConfig(), "127.0.0.1", 6379, 0);
		Jedis jedis = pool.getResource();
		long start = System.currentTimeMillis();
		for (int i = 0; i < 100000; i++) {
			String result = jedis.set("np" + i, "vp" + i);
		}
		long end = System.currentTimeMillis();
		pool.returnResource(jedis);
		System.out.println("POOL SET: " + ((end - start) / 1000.0) + " seconds");
		// 销毁
		jedis.close();
	}

	/**
	 * 事务方式(Transactions) 通过multi(开启一个事务)、exec(执行)、discard（放弃事务） Transaction
	 * SET: 0.926 seconds
	 */
	public void testMulti() {
		Jedis jedis = new Jedis("127.0.0.1", 6379);
		long start = System.currentTimeMillis();
		Transaction tx = jedis.multi();
		for (int i = 0; i < 100000; i++) {
			tx.set("t" + i, "v" + i);
		}
		List<Object> results = tx.exec();
		long end = System.currentTimeMillis();
		System.out.println("Transaction SET: " + ((end - start) / 1000.0) + " seconds");
		jedis.close();
	}

	/**
	 * 管道(Pipelining) 异步方式，一次发送多个指令 Pipelined SET: 0.742 seconds
	 */
	public void testPipelining() {
		Jedis jedis = new Jedis("127.0.0.1", 6379);
		Pipeline pipeline = jedis.pipelined();
		long start = System.currentTimeMillis();
		for (int i = 0; i < 100000; i++) {
			pipeline.set("p" + i, "p" + i);
		}
		List<Object> results = pipeline.syncAndReturnAll();
		long end = System.currentTimeMillis();
		System.out.println("Pipelined SET: " + ((end - start) / 1000.0) + " seconds");
		jedis.close();
	}

	/**
	 * 管道中调用事务 测试中会发生 java.lang.StackOverflowError
	 */
	public void testPipeliningWithMulti() {
		Jedis jedis = new Jedis("127.0.0.1", 6379);
		long start = System.currentTimeMillis();
		Pipeline pipeline = jedis.pipelined();
		pipeline.multi();
		// >10000次会内存溢出
		/*
		 * for (int i = 0; i < 100000; i++) { pipeline.set("" + i, "" + i); }
		 */
		for (int i = 0; i < 1000; i++) {
			pipeline.set("" + i, "" + i);
		}
		pipeline.exec();
		List<Object> results = pipeline.syncAndReturnAll();
		long end = System.currentTimeMillis();
		System.out.println("Pipelined transaction: " + ((end - start) / 1000.0) + " seconds");
		jedis.close();
	}

	/**
	 * 分布式直接连接同步调用 通过List<JedisShardInfo>中Info的顺序和key，计算hash,确定key为固定的Redis上
	 * Simple@Sharing SET: 95.106 seconds
	 */
	public void testshardNormal() {
		List<JedisShardInfo> shards = Arrays.asList(new JedisShardInfo("127.0.0.1", 6379),
				new JedisShardInfo("127.0.0.1", 6380));

		ShardedJedis sharding = new ShardedJedis(shards);

		long start = System.currentTimeMillis();
		for (int i = 0; i < 100000; i++) {
			String result = sharding.set("sn" + i, "n" + i);
		}
		long end = System.currentTimeMillis();
		System.out.println("Simple@Sharing SET: " + ((end - start) / 1000.0) + " seconds");

		sharding.close();
	}

	/**
	 * 分布式直接连接管道异步调用，key的分配结果同 分布式直接连接同步调用 相同 Pipelined@Sharing SET: 0.856
	 * seconds
	 */
	public void testshardPipelining() {
		List<JedisShardInfo> shards = Arrays.asList(new JedisShardInfo("127.0.0.1", 6379),
				new JedisShardInfo("127.0.0.1", 6380));

		ShardedJedis sharding = new ShardedJedis(shards);
		ShardedJedisPipeline pipeline = sharding.pipelined();
		long start = System.currentTimeMillis();
		for (int i = 0; i < 100000; i++) {
			pipeline.set("sp" + i, "p" + i);
		}
		List<Object> results = pipeline.syncAndReturnAll();
		long end = System.currentTimeMillis();
		System.out.println("Pipelined@Sharing SET: " + ((end - start) / 1000.0) + " seconds");

		sharding.close();
	}

	/**
	 * 分布式连接池同步调用
	 */
	public void testShardSimplePool() {
		List<JedisShardInfo> shards = Arrays.asList(new JedisShardInfo("127.0.0.1", 6379),
				new JedisShardInfo("127.0.0.1", 6380));

		ShardedJedisPool pool = new ShardedJedisPool(new JedisPoolConfig(), shards);

		ShardedJedis one = pool.getResource();

		long start = System.currentTimeMillis();
		for (int i = 0; i < 100000; i++) {
			String result = one.set("spn" + i, "n" + i);
		}
		long end = System.currentTimeMillis();
		pool.returnResource(one);
		System.out.println("Simple@Pool SET: " + ((end - start) / 1000.0) + " seconds");

		pool.destroy();
	}

	/**
	 * 分布式连接池异步调用相同，返回管道与分布式直接连接管道异步调用 Simple@Pool SET: 94.094 seconds
	 */
	public void testShardPoolWithPipelined() {
		List<JedisShardInfo> shards = Arrays.asList(new JedisShardInfo("localhost", 6379),
				new JedisShardInfo("localhost", 6380));

		ShardedJedisPool pool = new ShardedJedisPool(new JedisPoolConfig(), shards);

		ShardedJedis one = pool.getResource();

		ShardedJedisPipeline pipeline = one.pipelined();

		long start = System.currentTimeMillis();
		for (int i = 0; i < 100000; i++) {
			pipeline.set("sppn" + i, "n" + i);
		}
		List<Object> results = pipeline.syncAndReturnAll();
		long end = System.currentTimeMillis();
		pool.returnResource(one);
		System.out.println("Pipelined@Pool SET: " + ((end - start) / 1000.0) + " seconds");
		pool.destroy();
	}
}
