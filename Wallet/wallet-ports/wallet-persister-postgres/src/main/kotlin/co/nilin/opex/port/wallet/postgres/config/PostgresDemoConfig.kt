package co.nilin.opex.port.wallet.postgres.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.core.DatabaseClient

@Configuration
@Profile("demo")
class PostgresDemoConfig(db: DatabaseClient) {

    init {
        val initDb = db.sql {
            """
                insert into wallet_owner(id, uuid, title, level) values(1, '1', 'system', 'basic') ON CONFLICT DO NOTHING;
                insert into currency(name, symbol, precision) values('btc', 'btc', 0.000001) ON CONFLICT DO NOTHING;;
                insert into currency(name, symbol, precision) values('eth', 'eth', 0.00001) ON CONFLICT DO NOTHING;;
                insert into currency(name, symbol, precision) values('usdt', 'usdt', 0.01) ON CONFLICT DO NOTHING;; 
                insert into currency(name, symbol, precision) values('nln', 'nln', 1) ON CONFLICT DO NOTHING;; 
                insert into currency_rate(id, source_currency, dest_currency, rate) values(1, 'btc', 'nln', 5500000) ON CONFLICT DO NOTHING;
                insert into currency_rate(id, source_currency, dest_currency, rate) values(1, 'usdt', 'nln', 100) ON CONFLICT DO NOTHING;
                insert into currency_rate(id, source_currency, dest_currency, rate) values(1, 'btc', 'usdt', 55000) ON CONFLICT DO NOTHING;
                insert into currency_rate(id, source_currency, dest_currency, rate) values(1, 'eth', 'usdt', 3800) ON CONFLICT DO NOTHING;
                insert into wallet(id, owner, wallet_type, currency, balance) values(1, 1, 'main', 'btc', 10) ON CONFLICT DO NOTHING;
                insert into wallet(id, owner, wallet_type, currency, balance) values(2, 1, 'exchange', 'btc', 0) ON CONFLICT DO NOTHING; 
                insert into wallet(id, owner, wallet_type, currency, balance) values(3, 1, 'main', 'usdt', 550000) ON CONFLICT DO NOTHING; 
                insert into wallet(id, owner, wallet_type, currency, balance) values(4, 1, 'exchange', 'usdt', 0) ON CONFLICT DO NOTHING; 
                insert into wallet(id, owner, wallet_type, currency, balance) values(5, 1, 'main', 'nln', 100000000) ON CONFLICT DO NOTHING; 
                insert into wallet(id, owner, wallet_type, currency, balance) values(6, 1, 'exchange', 'nln', 0) ON CONFLICT DO NOTHING; 
                insert into wallet(id, owner, wallet_type, currency, balance) values(7, 1, 'main', 'eth', 10000) ON CONFLICT DO NOTHING;
                insert into wallet(id, owner, wallet_type, currency, balance) values(8, 1, 'exchange', 'eth', 0) ON CONFLICT DO NOTHING; 
                insert into user_limits (id, level, owner, action, wallet_type, daily_total, daily_count, monthly_total, monthly_count)values(1, null, 1, 'withdraw', 'main', 1000, 100, 10000, 1000) ON CONFLICT DO NOTHING;
            """
        }
        initDb // initialize the database
            .then()
            .subscribe() // execute
    }
}
