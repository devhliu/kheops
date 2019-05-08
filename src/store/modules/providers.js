import { HTTP } from '@/router/http'

// initial state
const state = {
	providers: [],
	provider: {}
}

const getters = {
	providers: state => state.providers
}

const actions = {
	getProviders ({ commit }, params) {
		commit('SET_PROVIDERS', params)
	},
	getProvider ({ commit }, params) {
		commit('SET_PROVIDER', params)
	},
	updateProvider ({ commit }, params) {
		commit('UPDATE_PROVIDER', params)
	},
	postProvider ({ dispatch }, params) {
		const albumID = params.albumID
		let queries = ''
		for (var key in params.query) {
			queries += `${key}=${params.query[key]}&`
		}
		return HTTP.post('/albums/' + albumID + '/reportprovider', queries, { headers: { 'Accept': 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' } }).then(res => {
			console.log(res)
		}).catch(res => {
			console.log(res)
		})
	}
}

const mutations = {
	SET_PROVIDERS (state, params) {
		state.providers = params.providers
	},
	SET_PROVIDER (state, params) {
		state.provider = params.provider
	}
}

export default {
	state,
	getters,
	actions,
	mutations
}
